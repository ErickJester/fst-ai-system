"""Capa 3 simplificada: reglas geometricas sin estimacion de pose (Modulo 6).

Las tres definiciones operativas del laboratorio son geometricas, y por eso se
pueden escribir como reglas sobre coordenadas:

  Escalamiento  movimiento hacia delante de las patas anteriores sobre las
                paredes de la camara.
  Natacion      movimiento de desplazamiento en la camara de natacion.
  Inmovilidad   el especimen no hace mayores intentos por escapar, excepto los
                movimientos necesarios para mantener el hocico fuera del agua.

Por que no hay modelo de pose
-----------------------------
El plan original de esta capa pedia nariz, base de la cola y las dos patas
anteriores con DeepLabCut o SLEAP. Entrenar eso es un proyecto en si mismo y
queda fuera de esta herramienta. Aqui los "puntos clave" se derivan de la
mascara del especimen --la misma que segmenta el Modulo 4-- en vez de una red:

  centroide          centro de masa de la mascara.
  caja del cuerpo    extremos de la mascara; su relacion alto/ancho es la
                     verticalidad del eje corporal.
  cuerpo anterior    la franja superior de la mascara. Cuando el especimen
                     trepa, las patas anteriores son lo mas alto y lo mas
                     pegado a la pared que hay en la mascara, asi que la
                     franja superior sirve de sustituto sin pose.
  masa sobre el agua fraccion de la mascara por encima de la linea de agua
                     del Modulo 3.

Es un sustituto, no un equivalente: la mascara no distingue una pata de la
cabeza, y un especimen que asoma el hocico para respirar produce masa sobre el
agua igual que uno que trepa. Por eso la regla de escalamiento exige las tres
senales a la vez, y por eso esta capa entra al consenso del Modulo 7 como un
votante mas, no como juez.

Independencia respecto a la Capa 2
----------------------------------
Esta capa mide sobre la MISMA mascara que el detector de movimiento del
Modulo 4, a proposito. Lo que las hace fuentes distintas es la regla de
decision, no la segmentacion: la Capa 2 mira cuanta area cambia entre cuadros
y decide inmovil o activo; esta mira donde esta esa area y que forma tiene, y
decide entre las tres conductas. Segmentar dos veces por separado haria que
discreparan por el recorte y no por la conducta, y el consenso del Modulo 7 no
podria distinguir una cosa de la otra. Es un limite real de la independencia
entre capas y hay que tenerlo presente al leer el consenso.

Unidades
--------
Las dimensiones del recipiente cambian entre montajes, asi que ningun umbral
va en pixeles. Todo se mide en el recorte enderezado y se normaliza:

  desplazamiento     fracciones del ALTO de la region por segundo. Por segundo
                     y no por cuadro, para que no dependa de los cuadros por
                     segundo del archivo.
  proximidad_pared   fraccion del ANCHO de la region. Menor es mas pegado.
  verticalidad       alto/ancho de la caja del cuerpo, adimensional.
  sobre_agua         fraccion del area del cuerpo, adimensional.

Umbrales
--------
Ninguno trae un valor fijo de fabrica. En None se calculan por el metodo de
Otsu sobre la distribucion del propio video, igual que el umbral de actividad
del Modulo 4, y el reporte declara cuanto separa cada corte: Otsu siempre
devuelve uno, incluso sobre una distribucion de una sola joroba. Fijarlos a
mano tras puntuar unos videos sigue siendo lo correcto.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np

from . import estabilizacion
from .deteccion_movimiento import (
    HOLGURA_PRIOR,
    PRIOR_INMOVILIDAD,
    REFERENCIA_UNIMODAL,
    UMBRAL_SEPARACION,
    Parametros as ParametrosSegmentacion,
    construir_fondos,
    mascara_especimen,
    matriz_rectificacion,
    razon_de_varianzas,
    rectificar,
    tamano_rectificado,
    umbral_otsu,
)

# Orden en que se evaluan las reglas. Tambien es el orden de desempate del
# voto por bloque, y se declara para que sea auditable.
#
# El escalamiento va PRIMERO, antes que la inmovilidad, y no es un detalle: un
# especimen trepando por la pared apenas mueve su centro de masa --sube y baja
# pegado al cilindro-- asi que la regla de inmovilidad lo atraparia antes de
# que la de escalamiento llegara a verlo. La definicion operativa lo prohibe:
# inmovilidad es "no hacer mayores intentos por escapar", y trepar es
# precisamente un intento de escapar. Medido sobre el video sintetico de
# prueba, la fase de escalamiento tiene el desplazamiento MAS BAJO de las
# tres: con el orden inverso, el 100 % del video salia inmovil.
CLASES = ("escalamiento", "inmovilidad", "nado")

# Percentil al que se recorta la cola de una distribucion antes de calcular su
# corte de Otsu. Las medidas por cuadro tienen cola larga --un salto de
# segmentacion mueve el centroide de golpe-- y Otsu reparte los 256 niveles
# entre el minimo y el MAXIMO, asi que un solo valor extremo comprime toda la
# masa de la distribucion en los primeros niveles y arrastra el corte hacia
# arriba. Medido sobre el video sintetico: sin recorte el corte de
# desplazamiento salia en 0.83 con la mediana de la fase de nado en 0.35, y
# todo el video caia del lado inmovil.
PERCENTIL_RECORTE = 99.0


@dataclass
class Umbrales:
    """Umbrales de las tres reglas. NINGUNO trae valor de fabrica.

    En None se calculan por Otsu sobre la distribucion del propio video. No se
    fija ningun numero aqui porque un umbral en estas unidades depende del
    montaje --tamano del cilindro en el cuadro, distancia de la camara,
    tamano del especimen-- y no es transferible entre grabaciones.
    """

    # Por debajo de esto el cuadro es inmovil.
    # Unidad: fracciones del alto de la region por segundo.
    desplazamiento: float | None = None

    # Por debajo de esto el cuerpo anterior esta pegado a la pared.
    # Unidad: fraccion del ancho de la region.
    proximidad_pared: float | None = None

    # Por encima de esto el eje corporal esta vertical.
    # Unidad: alto/ancho de la caja del cuerpo.
    verticalidad: float | None = None

    # Por encima de esto hay masa del cuerpo rompiendo la superficie.
    # Unidad: fraccion del area del cuerpo.
    sobre_agua: float | None = None


@dataclass
class Parametros:
    """Parametros del analisis. Puntos de partida a calibrar, no resultados."""

    umbrales: Umbrales = field(default_factory=Umbrales)

    # Duracion del bloque. 5 s es el estandar de Detke et al. (1995), y es
    # tambien la unidad del Modulo 4. El Modulo 5 trabaja en clips de 3 s, asi
    # que el Modulo 7 tendra que alinear las dos rejillas; por eso es un
    # parametro y no una constante.
    segundos_por_bloque: float = 5.0

    # Los tres siguientes son los de la segmentacion del Modulo 4 y se pasan
    # tal cual, para que ambas capas vean exactamente la misma mascara.
    umbral_binarizacion: int = 40
    muestras_fondo: int = 40
    apertura: int = 1

    # Area minima de la mascara para dar el cuadro por medible, en fraccion
    # del area de la region. Es el mismo 0.5 % con el que el Modulo 4 avisa de
    # bloques sin especimen; por debajo de eso no hay cuerpo del que medir
    # forma ni posicion.
    area_minima: float = 0.005

    # Franja superior de la mascara que hace de cuerpo anterior, en fraccion
    # de la altura del cuerpo. Punto de partida a calibrar: un tercio.
    fraccion_superior: float = 1.0 / 3.0

    estabilizar: bool = True
    cadencia_camara: int = 30
    lado_maximo: int = 480

    def cuadros_por_bloque(self, fps: float) -> int:
        return max(1, int(round(self.segundos_por_bloque * fps)))

    def segmentacion(self) -> ParametrosSegmentacion:
        """Los parametros con los que el Modulo 4 construye la mascara."""
        return ParametrosSegmentacion(
            segundos_por_bloque=self.segundos_por_bloque,
            umbral_binarizacion=self.umbral_binarizacion,
            muestras_fondo=self.muestras_fondo,
            cadencia_camara=self.cadencia_camara,
            estabilizar=self.estabilizar,
            lado_maximo=self.lado_maximo,
            apertura=self.apertura,
        )


@dataclass
class Medidas:
    """Lo medido en un cuadro. Las unidades estan en el encabezado del modulo."""

    cuadro: int
    area: float
    centro_x: float
    centro_y: float
    verticalidad: float
    proximidad_pared: float
    # None cuando la region no tiene linea de agua dibujada.
    sobre_agua: float | None = None
    # None en el primer cuadro medido de la region: no hay con que comparar.
    desplazamiento: float | None = None
    clase: str = "sin datos"


# ---------------------------------------------------------------- las reglas
#
# Las tres reglas son funciones puras de las medidas de un cuadro y de los
# umbrales. Estan separadas y nombradas a proposito: son la traduccion literal
# de las definiciones operativas del laboratorio y tienen que poder auditarse
# una por una.


def es_inmovilidad(m: Medidas, u: Umbrales) -> bool:
    """El especimen no hace mayores intentos por escapar.

    El centroide del cuerpo se mueve por debajo del umbral. Los movimientos
    para mantener el hocico fuera del agua desplazan poco el centro de masa,
    que es justo lo que la definicion operativa permite.
    """
    if m.desplazamiento is None or u.desplazamiento is None:
        return False
    return m.desplazamiento < u.desplazamiento


def es_escalamiento(m: Medidas, u: Umbrales) -> bool:
    """Patas anteriores hacia delante sobre las paredes de la camara.

    Exige las tres senales a la vez, porque cada una por separado tiene un
    modo de fallo conocido: pegado a la pared tambien queda el especimen que
    nada en circulos, vertical tambien queda el que bucea, y masa sobre el
    agua tambien produce el que solo asoma el hocico para respirar.

    Sin linea de agua dibujada la tercera senal no se puede evaluar y la regla
    decide con las otras dos. El reporte lo avisa: es una regla mas debil.
    """
    if u.proximidad_pared is None or u.verticalidad is None:
        return False
    if m.proximidad_pared >= u.proximidad_pared:
        return False
    if m.verticalidad <= u.verticalidad:
        return False
    if m.sobre_agua is None or u.sobre_agua is None:
        return True
    return m.sobre_agua > u.sobre_agua


def es_nado(m: Medidas, u: Umbrales) -> bool:
    """Movimiento de desplazamiento en la camara de natacion.

    Es la clase residual: hay movimiento y no es escalamiento. Se escribe
    aparte para que las tres definiciones esten en el mismo sitio, aunque
    `clasificar_cuadro` llegue a ella por descarte.
    """
    return not es_escalamiento(m, u) and not es_inmovilidad(m, u)


def clasificar_cuadro(m: Medidas, u: Umbrales) -> str:
    """Aplica las tres reglas en el orden de CLASES y devuelve la clase.

    El escalamiento se comprueba antes que la inmovilidad porque trepar mueve
    poco el centro de masa; ver la nota de CLASES.
    """
    if m.desplazamiento is None:
        return "sin datos"
    if es_escalamiento(m, u):
        return "escalamiento"
    if es_inmovilidad(m, u):
        return "inmovilidad"
    return "nado"


# ------------------------------------------------------------------ medicion


def _linea_rectificada(region: dict, matriz_camara, tamano) -> tuple | None:
    """Lleva la linea de agua al espacio del recorte enderezado.

    La linea se arrastra primero con el movimiento de la camara, igual que las
    esquinas, y despues se pasa por la misma homografia que la imagen.
    """
    linea = region.get("lineaAgua")
    if not linea or len(linea) != 2:
        return None
    esquinas = estabilizacion.aplicar(matriz_camara, region["esquinas"])
    puntos = estabilizacion.aplicar(matriz_camara, linea)
    homografia = matriz_rectificacion(esquinas, tamano)
    arreglo = np.float32([[[p["x"], p["y"]] for p in puntos]])
    (a, b) = cv2.perspectiveTransform(arreglo, homografia)[0]
    if abs(float(b[0]) - float(a[0])) < 1e-6:
        # Linea vertical en el espacio rectificado: no separa arriba de abajo.
        return None
    return (float(a[0]), float(a[1]), float(b[0]), float(b[1]))


def _altura_del_agua(linea: tuple, xs: np.ndarray) -> np.ndarray:
    """Altura de la superficie del agua en cada columna x del recorte."""
    x0, y0, x1, y1 = linea
    pendiente = (y1 - y0) / (x1 - x0)
    return y0 + (xs - x0) * pendiente


def medir(
    mascara: np.ndarray,
    cuadro: int,
    linea: tuple | None,
    p: Parametros,
) -> Medidas | None:
    """Extrae los puntos clave de la mascara. None si no hay cuerpo medible."""
    alto_r, ancho_r = mascara.shape
    ys, xs = np.nonzero(mascara)
    if xs.size == 0:
        return None
    area = float(xs.size) / float(alto_r * ancho_r)
    if area < p.area_minima:
        return None

    x0, x1 = int(xs.min()), int(xs.max())
    y0, y1 = int(ys.min()), int(ys.max())
    verticalidad = float(y1 - y0 + 1) / float(x1 - x0 + 1)

    # Cuerpo anterior: la franja superior de la mascara. La proximidad se mide
    # a la pared mas cercana de las dos, porque el especimen puede trepar por
    # cualquiera de los dos lados del cilindro.
    corte = y0 + p.fraccion_superior * (y1 - y0 + 1)
    anteriores = xs[ys <= corte]
    if anteriores.size == 0:
        anteriores = xs
    proximidad = min(int(anteriores.min()), (ancho_r - 1) - int(anteriores.max()))
    proximidad_pared = float(proximidad) / float(ancho_r)

    sobre_agua = None
    if linea is not None:
        sobre_agua = float(np.mean(ys < _altura_del_agua(linea, xs)))

    return Medidas(
        cuadro=cuadro,
        area=area,
        centro_x=float(xs.mean()),
        centro_y=float(ys.mean()),
        verticalidad=verticalidad,
        proximidad_pared=proximidad_pared,
        sobre_agua=sobre_agua,
    )


# ------------------------------------------------------------------ umbrales


@dataclass
class UmbralResuelto:
    nombre: str
    valor: float
    origen: str
    muestras: int
    separacion: float | None = None
    aviso: str | None = None

    def to_dict(self) -> dict:
        return {
            "nombre": self.nombre,
            "valor": round(self.valor, 5),
            "origen": self.origen,
            "muestras": self.muestras,
            "separacion": None if self.separacion is None else round(self.separacion, 3),
            "aviso": self.aviso,
        }


def _resolver_umbral(nombre: str, fijado: float | None, valores: np.ndarray) -> UmbralResuelto:
    """Toma el umbral fijado a mano, o lo calcula por Otsu y dice cuanto separa.

    La cola se recorta al percentil PERCENTIL_RECORTE antes del corte: Otsu
    reparte sus niveles entre el minimo y el maximo, y un solo valor extremo
    --un salto de segmentacion-- arrastra el corte hacia arriba. La razon de
    varianzas se calcula sobre los mismos valores recortados, para que describa
    el corte que realmente se aplico.
    """
    if fijado is not None:
        return UmbralResuelto(nombre, float(fijado), "fijado a mano", int(valores.size))
    if valores.size < 2:
        return UmbralResuelto(
            nombre, 0.0, "sin datos suficientes", int(valores.size),
            aviso=f"No hubo muestras para calcular el umbral de {nombre}.",
        )
    acotados = np.minimum(valores, float(np.percentile(valores, PERCENTIL_RECORTE)))
    valor = umbral_otsu(acotados)
    resuelto = UmbralResuelto(
        nombre, valor,
        "calculado por el método de Otsu sobre los cuadros de este video, con la "
        f"cola recortada al percentil {PERCENTIL_RECORTE:.0f}",
        int(valores.size),
        separacion=razon_de_varianzas(acotados, valor),
    )
    if resuelto.separacion is None:
        resuelto.aviso = (
            f"El corte de {nombre} dejó todos los cuadros de un solo lado: el umbral "
            "automático no está separando nada."
        )
    elif resuelto.separacion < UMBRAL_SEPARACION:
        resuelto.aviso = (
            f"La distribución de {nombre} no se separa en dos grupos (razón de varianzas "
            f"{resuelto.separacion:.2f}; una distribución de una sola joroba cortada en su "
            f"media ya da {REFERENCIA_UNIMODAL:.2f}). Otsu partió por la mitad algo que no "
            "tiene dos modos, así que este umbral no es interpretable todavía: fíjalo a "
            "mano tras puntuar unos videos."
        )
    return resuelto


def _calcular_umbrales(medidas: list[Medidas], u: Umbrales) -> dict[str, UmbralResuelto]:
    """Resuelve los cuatro umbrales sobre la distribucion del propio video.

    Los cuatro se calculan sobre TODOS los cuadros medidos. Una version previa
    calculaba los tres del escalamiento solo sobre los cuadros activos --los
    que superan el umbral de desplazamiento-- suponiendo que trepar es una
    forma de actividad. Es falso: trepar mueve poco el centro de masa, asi que
    esos cuadros quedaban fuera de la distribucion y el corte de verticalidad
    se calculaba sin ver ni un solo cuadro de trepada. Medido sobre el video
    sintetico, la verticalidad mediana de la fase de escalamiento es 1.62
    contra 0.52 de las otras dos --una separacion inmensa-- y aun asi el corte
    salia en 0.52 y no separaba nada.

    Un solo juego de umbrales para todas las regiones del video, por la misma
    razon que en el Modulo 4: describen las condiciones de grabacion, no al
    especimen. Uno por region normalizaria a cada especimen contra si mismo y
    borraria la diferencia entre especimenes que el experimento mide.
    """
    con_desplazamiento = [m for m in medidas if m.desplazamiento is not None]
    con_agua = [m for m in medidas if m.sobre_agua is not None]
    return {
        "desplazamiento": _resolver_umbral(
            "desplazamiento", u.desplazamiento,
            np.array([m.desplazamiento for m in con_desplazamiento], dtype=np.float64),
        ),
        "proximidad_pared": _resolver_umbral(
            "proximidad a la pared", u.proximidad_pared,
            np.array([m.proximidad_pared for m in medidas], dtype=np.float64),
        ),
        "verticalidad": _resolver_umbral(
            "verticalidad", u.verticalidad,
            np.array([m.verticalidad for m in medidas], dtype=np.float64),
        ),
        "sobre_agua": _resolver_umbral(
            "masa sobre el agua", u.sobre_agua,
            np.array([m.sobre_agua for m in con_agua], dtype=np.float64),
        ),
    }


def _umbrales_de(resueltos: dict[str, UmbralResuelto], hay_agua: bool) -> Umbrales:
    return Umbrales(
        desplazamiento=resueltos["desplazamiento"].valor,
        proximidad_pared=resueltos["proximidad_pared"].valor,
        verticalidad=resueltos["verticalidad"].valor,
        sobre_agua=resueltos["sobre_agua"].valor if hay_agua else None,
    )


# ------------------------------------------------------------------- bloques


@dataclass
class Bloque:
    indice: int
    cuadro_inicio: int
    cuadro_fin: int
    segundo_inicio: float
    segundo_fin: float
    votos: dict = field(default_factory=dict)
    cuadros_medidos: int = 0
    cuadros_sin_cuerpo: int = 0
    margen_voto: float = 0.0
    empate: bool = False
    medianas: dict = field(default_factory=dict)
    clase: str = "sin datos"

    def to_dict(self) -> dict:
        return {
            "bloque": self.indice,
            "cuadro_inicio": self.cuadro_inicio,
            "cuadro_fin": self.cuadro_fin,
            "segundo_inicio": round(self.segundo_inicio, 2),
            "segundo_fin": round(self.segundo_fin, 2),
            "clase": self.clase,
            "votos": self.votos,
            "cuadros_medidos": self.cuadros_medidos,
            "cuadros_sin_cuerpo": self.cuadros_sin_cuerpo,
            "margen_voto": round(self.margen_voto, 4),
            "empate": self.empate,
            "medianas": self.medianas,
        }


def _consolidar(bloque: Bloque, medidas: list[Medidas], cuadros_del_bloque: int) -> None:
    """Voto mayoritario del bloque, siguiendo a Detke et al. (1995).

    El metodo no mide duracion de episodios: asigna a cada bloque la conducta
    predominante. El margen del voto viaja en el reporte porque es lo que el
    Modulo 7 puede comparar contra la confianza del modelo del Modulo 5.

    Un empate se resuelve por el orden de CLASES, que es el orden en que se
    evaluan las reglas, y se marca: un bloque empatado deberia entrar a la cola
    de revision aunque las demas capas coincidan con el.
    """
    validas = [m for m in medidas if m.clase in CLASES]
    bloque.cuadros_medidos = len(validas)
    # Los cuadros sin cuerpo medible no llegan a `medidas`: se descartaron al
    # medir, asi que se cuentan contra el total de cuadros que abarca el bloque.
    bloque.cuadros_sin_cuerpo = max(0, cuadros_del_bloque - len(medidas))
    bloque.votos = {c: sum(m.clase == c for m in validas) for c in CLASES}
    if not validas:
        return

    mayor = max(bloque.votos.values())
    ganadoras = [c for c in CLASES if bloque.votos[c] == mayor]
    bloque.clase = ganadoras[0]
    bloque.empate = len(ganadoras) > 1
    bloque.margen_voto = mayor / len(validas)
    bloque.medianas = {
        "desplazamiento": round(
            float(np.median([m.desplazamiento for m in validas if m.desplazamiento is not None])), 5
        ),
        "proximidad_pared": round(float(np.median([m.proximidad_pared for m in validas])), 5),
        "verticalidad": round(float(np.median([m.verticalidad for m in validas])), 4),
        "area": round(float(np.median([m.area for m in validas])), 5),
    }
    con_agua = [m.sobre_agua for m in validas if m.sobre_agua is not None]
    if con_agua:
        bloque.medianas["sobre_agua"] = round(float(np.median(con_agua)), 4)


@dataclass
class ResultadoRegion:
    region: int
    bloques: list[Bloque] = field(default_factory=list)
    tiene_linea_agua: bool = False
    avisos: list[str] = field(default_factory=list)

    @property
    def medidos(self) -> list[Bloque]:
        return [b for b in self.bloques if b.clase in CLASES]

    def to_dict(self) -> dict:
        medidos = self.medidos
        conteo = {c: sum(b.clase == c for b in medidos) for c in CLASES}
        total = len(medidos) or 1
        return {
            "region": self.region,
            "bloques_totales": len(self.bloques),
            "bloques_medidos": len(medidos),
            "tiene_linea_agua": self.tiene_linea_agua,
            "conteo": conteo,
            "porcentajes": {c: round(100.0 * v / total, 1) for c, v in conteo.items()},
            "margen_voto_medio": round(
                float(np.mean([b.margen_voto for b in medidos])) if medidos else 0.0, 4
            ),
            "bloques_empatados": sum(b.empate for b in medidos),
            "compuerta_cordura": self._compuerta(conteo, len(medidos)),
            "avisos": self.avisos,
            "bloques": [b.to_dict() for b in self.bloques],
        }

    def _compuerta(self, conteo: dict, total: int) -> dict:
        """Capa 0: contrasta la inmovilidad contra el prior del laboratorio.

        Solo se compuerta la inmovilidad. Es la unica clase para la que hay un
        rango medido y citable de este laboratorio (Tabla 4 de la tesis de la
        Seccion de Posgrado, ENMyH-IPN, que el Modulo 4 ya usa). Para nado y
        escalamiento no hay una fuente igual de firme, y poner un rango sin
        respaldo convertiria la compuerta en ruido.
        """
        if not total:
            return {"sospechoso": False, "mensaje": "Ningún bloque pudo medirse."}
        pct = 100.0 * conteo["inmovilidad"] / total
        bajo, alto = PRIOR_INMOVILIDAD
        dentro = bajo - HOLGURA_PRIOR <= pct <= alto + HOLGURA_PRIOR
        mensaje = (
            f"{pct:.1f} % de bloques inmóviles. El rango medido por el laboratorio en la "
            f"prueba de 5 min es {bajo}-{alto} %"
            + (
                "; el resultado es compatible."
                if dentro
                else f", y este resultado queda fuera incluso con {HOLGURA_PRIOR} puntos de "
                     "holgura. Revisa la región de interés, la línea de agua y el umbral de "
                     "desplazamiento antes de usarlo."
            )
        )
        return {
            "sospechoso": not dentro,
            "porcentaje_inmovilidad": round(pct, 1),
            "prior_laboratorio": list(PRIOR_INMOVILIDAD),
            "holgura": HOLGURA_PRIOR,
            "mensaje": mensaje,
        }


def _avisos_de_region(r: ResultadoRegion, medidas: list[Medidas]) -> None:
    """Condiciones bajo las que el resultado de esta region no se debe creer."""
    if not r.tiene_linea_agua:
        r.avisos.append(
            "Esta región no tiene línea de agua dibujada (W), así que la regla de "
            "escalamiento corre con dos señales de tres: proximidad a la pared y "
            "verticalidad, sin comprobar que haya masa rompiendo la superficie."
        )
    medidos = r.medidos
    if not medidos:
        r.avisos.append("Ningún bloque pudo medirse en esta región.")
        return

    sin_cuerpo = sum(b.cuadros_sin_cuerpo for b in r.bloques)
    total_cuadros = sin_cuerpo + sum(b.cuadros_medidos for b in r.bloques)
    if total_cuadros and sin_cuerpo / total_cuadros > 0.10:
        r.avisos.append(
            f"{100.0 * sin_cuerpo / total_cuadros:.0f} % de los cuadros quedaron sin "
            "cuerpo medible dentro de la región. O el espécimen no está ahí, o el umbral "
            "de binarización es demasiado alto."
        )

    escalamiento = sum(b.clase == "escalamiento" for b in medidos)
    if escalamiento == 0:
        r.avisos.append(
            "Ningún bloque salió como escalamiento. Puede que el espécimen no trepara, "
            "pero también que los umbrales automáticos no separen trepar de nadar: el "
            "escalamiento es la clase minoritaria y Otsu supone dos grupos del mismo orden."
        )
    elif escalamiento > len(medidos) / 2:
        r.avisos.append(
            f"{100.0 * escalamiento / len(medidos):.0f} % de los bloques salieron como "
            "escalamiento, muy por encima de lo que reporta la literatura para esta prueba. "
            "Revisa que la región encierre el cilindro y no su borde exterior."
        )

    empatados = sum(b.empate for b in medidos)
    if empatados:
        r.avisos.append(
            f"{empatados} bloques con empate en el voto: se resolvieron por el orden de "
            "las reglas. Conviene que el Módulo 7 los mande a revisión aunque las demás "
            "capas coincidan."
        )


# ------------------------------------------------------------------ analisis


def analizar(
    ruta: str,
    regiones: list[dict],
    fps: float,
    total_cuadros: int,
    cuadro_referencia: int = 0,
    cuadro_inicio: int = 0,
    cuadro_fin: int | None = None,
    parametros: Parametros | None = None,
) -> dict:
    """Corre las reglas geometricas sobre un video y devuelve el reporte."""
    p = parametros or Parametros()
    if not regiones:
        raise ValueError("Hace falta al menos una región de interés para analizar.")
    if fps <= 0:
        raise ValueError("Los cuadros por segundo deben ser mayores que cero.")

    fin = total_cuadros if cuadro_fin is None else min(cuadro_fin, total_cuadros)
    if fin - cuadro_inicio < 2:
        raise ValueError("El rango de análisis es demasiado corto.")

    cap = cv2.VideoCapture(ruta)
    if not cap.isOpened():
        raise RuntimeError(f"OpenCV no pudo abrir el video: {ruta}")

    try:
        cap.set(cv2.CAP_PROP_POS_FRAMES, cuadro_referencia)
        ok, cuadro = cap.read()
        if not ok:
            raise RuntimeError(f"No se pudo leer el cuadro de referencia {cuadro_referencia}.")
        gris_ref = cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY)
        alto, ancho = gris_ref.shape

        seguidor = None
        aviso_camara = None
        if p.estabilizar:
            mascara = estabilizacion.mascara_fuera_de(regiones, alto, ancho)
            seguidor = estabilizacion.SeguidorCamara(gris_ref, mascara)
            if not seguidor.utilizable:
                seguidor = None
                aviso_camara = (
                    "No hay textura suficiente fuera de las regiones para seguir el "
                    "movimiento de la cámara; el análisis corre sin compensarlo."
                )

        tamanos = [tamano_rectificado(r["esquinas"], p.lado_maximo) for r in regiones]
        fondos = construir_fondos(
            cap, regiones, tamanos, seguidor, p.muestras_fondo, cuadro_inicio, fin
        )
        por_region, leidos_hasta = _recorrer(
            cap, regiones, tamanos, fondos, seguidor, p, fps, cuadro_inicio, fin
        )
    finally:
        cap.release()

    todas = [m for medidas in por_region for m in medidas]
    if not todas:
        raise RuntimeError(
            "No se pudo medir ningún cuadro: la región no está captando al espécimen."
        )

    resueltos = _calcular_umbrales(todas, p.umbrales)
    resultados = []
    for i, medidas in enumerate(por_region):
        hay_agua = regiones[i].get("lineaAgua") is not None
        umbrales = _umbrales_de(resueltos, hay_agua)
        for m in medidas:
            m.clase = clasificar_cuadro(m, umbrales)
        resultados.append(
            _bloques_de_region(
                i + 1, medidas, p, fps, cuadro_inicio, fin, leidos_hasta, hay_agua
            )
        )

    for r in resultados:
        if aviso_camara:
            r.avisos.insert(0, aviso_camara)

    return {
        "video": ruta,
        "fps": round(fps, 4),
        "cuadro_referencia": cuadro_referencia,
        "cuadro_inicio": cuadro_inicio,
        "cuadro_fin": fin,
        "segundos_por_bloque": p.segundos_por_bloque,
        "cuadros_por_bloque": p.cuadros_por_bloque(fps),
        "clases": list(CLASES),
        "parametros": {
            "umbral_binarizacion": p.umbral_binarizacion,
            "muestras_fondo": p.muestras_fondo,
            "area_minima": p.area_minima,
            "fraccion_superior": round(p.fraccion_superior, 4),
            "estabilizar": p.estabilizar,
            "lado_maximo": p.lado_maximo,
            "nota": "Puntos de partida a calibrar con los videos del laboratorio.",
        },
        "umbrales": {k: v.to_dict() for k, v in resueltos.items()},
        "unidades": {
            "desplazamiento": "fracciones del alto de la región por segundo",
            "proximidad_pared": "fracción del ancho de la región; menor es más pegado",
            "verticalidad": "alto/ancho de la caja del cuerpo",
            "sobre_agua": "fracción del área del cuerpo por encima de la línea de agua",
        },
        "regiones": [r.to_dict() for r in resultados],
    }


def _recorrer(cap, regiones, tamanos, fondos, seguidor, p: Parametros, fps, inicio, fin):
    """Una sola pasada secuencial: mide cada cuadro de cada region.

    Devuelve las medidas por region y hasta que cuadro se pudo leer, que no es
    `fin` si el archivo se corta antes: los cuadros que nunca se leyeron no son
    cuadros sin especimen y no deben contarse como tales.
    """
    por_region: list[list[Medidas]] = [[] for _ in regiones]
    previos: list[Medidas | None] = [None] * len(regiones)
    parametros_mascara = p.segmentacion()

    cap.set(cv2.CAP_PROP_POS_FRAMES, inicio)
    matriz = None
    leidos_hasta = inicio

    for n in range(inicio, fin):
        ok, cuadro = cap.read()
        if not ok:
            break
        leidos_hasta = n + 1
        gris = cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY)

        if seguidor is not None and (n - inicio) % p.cadencia_camara == 0:
            nueva, inliers = seguidor.matriz(gris)
            if nueva is not None and inliers >= estabilizacion.MIN_INLIERS:
                matriz = nueva

        for i, region in enumerate(regiones):
            esquinas = estabilizacion.aplicar(matriz, region["esquinas"])
            recorte = rectificar(gris, esquinas, tamanos[i])
            mascara = mascara_especimen(recorte, fondos[i], parametros_mascara)
            linea = _linea_rectificada(region, matriz, tamanos[i])
            medida = medir(mascara, n, linea, p)
            if medida is None:
                continue
            anterior = previos[i]
            if anterior is not None:
                segundos = (n - anterior.cuadro) / fps
                distancia = float(
                    np.hypot(
                        medida.centro_x - anterior.centro_x,
                        medida.centro_y - anterior.centro_y,
                    )
                )
                # Normalizado por el alto de la region y por el tiempo real
                # transcurrido, que no es 1/fps si hubo cuadros sin cuerpo.
                medida.desplazamiento = distancia / tamanos[i][1] / segundos
            previos[i] = medida
            por_region[i].append(medida)

    return por_region, leidos_hasta


def _bloques_de_region(
    numero: int,
    medidas: list[Medidas],
    p: Parametros,
    fps,
    inicio,
    fin,
    leidos_hasta: int,
    hay_agua: bool,
) -> ResultadoRegion:
    """Reparte las medidas en bloques de 5 s y consolida cada uno."""
    resultado = ResultadoRegion(region=numero, tiene_linea_agua=hay_agua)
    por_bloque = p.cuadros_por_bloque(fps)
    reparto: dict[int, list[Medidas]] = {}
    for m in medidas:
        reparto.setdefault((m.cuadro - inicio) // por_bloque, []).append(m)

    for b, arranque in enumerate(range(inicio, fin, por_bloque)):
        cierre = min(arranque + por_bloque, fin)
        bloque = Bloque(
            indice=b + 1,
            cuadro_inicio=arranque,
            cuadro_fin=cierre - 1,
            segundo_inicio=(arranque - inicio) / fps,
            segundo_fin=(cierre - 1 - inicio) / fps,
        )
        _consolidar(bloque, reparto.get(b, []), max(0, min(cierre, leidos_hasta) - arranque))
        resultado.bloques.append(bloque)

    _avisos_de_region(resultado, medidas)
    return resultado
