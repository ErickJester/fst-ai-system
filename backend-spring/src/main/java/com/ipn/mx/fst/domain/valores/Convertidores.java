// Carpeta lógica de esta clase: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// AttributeConverter<A, B>: la interfaz de JPA para "traductor entre el tipo A de Java y el tipo B de la
// columna". Aquí siempre es <AlgúnEnum, String>.
import jakarta.persistence.AttributeConverter;
// @Converter: marca una clase como conversor de JPA para que Hibernate la reconozca.
import jakarta.persistence.Converter;

// Un conversor JPA por enum: guarda y lee en la base el texto EXACTO del dominio (valor()), no el nombre de la constante.
// Cada uno deja pasar el null (Segundo.conducta es null cuando no se ve al espécimen) y, si la base trajera un texto que
// el enum no conoce, desdeValor lanza una IllegalArgumentException con el dominio y el texto. Se usan con
// @Convert(converter = Convertidores.XxxConvertidor.class) en cada campo; la columna conserva su columnDefinition con
// el nombre del dominio, que es lo que necesita ddl-auto=validate.
//
// Por qué hacen falta: JPA sabe guardar un enum de dos maneras, y ninguna sirve aquí.
//   @Enumerated(STRING) guardaría el NOMBRE de la constante ("EN_COLA"), y la columna espera "en cola".
//   @Enumerated(ORDINAL) guardaría la posición (0, 1, 2...), que además se rompe al reordenar el enum.
// Con un conversor se controla el texto exacto de los dos lados, que es lo que exige el dominio del esquema.
//
// Por qué están todos en un archivo: son nueve clases de ocho líneas con el mismo cuerpo. Repartidas en
// nueve archivos pesarían más de leer que juntas. Se nombran Convertidores.TipoGrupoConvertidor, etc.
// final + constructor privado: la clase es solo un contenedor, no se instancia nunca.
public final class Convertidores {

	// Impide escribir "new Convertidores()". Lo único que se usa de esta clase son sus clases anidadas.
	private Convertidores() {
	}

	// --- A partir de aquí, nueve clases con el MISMO cuerpo, una por enum del paquete. ---
	// La primera está comentada línea por línea; las ocho siguientes son idénticas salvo el enum y el
	// nombre del dominio, y llevan solo la nota de qué traducen.

	// Traduce TipoGrupo <-> el texto de la columna del dominio tipo_grupo.
	// @Converter sin "autoApply = true" a propósito: el conversor se aplica solo donde el campo lo pide con
	// @Convert. Así un campo de ese mismo tipo podría guardarse de otra forma si alguna vez hiciera falta.
	// static: la clase anidada no necesita una instancia de Convertidores para existir.
	@Converter
	public static class TipoGrupoConvertidor implements AttributeConverter<TipoGrupo, String> {
		// DE JAVA A LA BASE. Lo llama Hibernate al guardar. Devuelve el texto del dominio, no el nombre
		// de la constante. El null pasa tal cual: una columna que admite null debe poder recibirlo.
		@Override
		public String convertToDatabaseColumn(TipoGrupo atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// DE LA BASE A JAVA. Lo llama Hibernate al leer. desdeValor busca la constante que lleva ese texto
		// y, si no existe ninguna, lanza IllegalArgumentException en vez de devolver null en silencio:
		// un texto desconocido significa que la base y el código ya no coinciden, y eso hay que verlo.
		@Override
		public TipoGrupo convertToEntityAttribute(String texto) {
			return texto == null ? null : TipoGrupo.desdeValor(texto);
		}
	}

	// Traduce SesionVideo <-> el texto del dominio sesion_video ('Dia 1', 'Dia 2').
	@Converter
	public static class SesionVideoConvertidor implements AttributeConverter<SesionVideo, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(SesionVideo atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public SesionVideo convertToEntityAttribute(String texto) {
			return texto == null ? null : SesionVideo.desdeValor(texto);
		}
	}

	// Traduce FormatoReporte <-> el texto del dominio formato_reporte ('CSV', 'XLSX', 'PDF').
	@Converter
	public static class FormatoReporteConvertidor implements AttributeConverter<FormatoReporte, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(FormatoReporte atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public FormatoReporte convertToEntityAttribute(String texto) {
			return texto == null ? null : FormatoReporte.desdeValor(texto);
		}
	}

	// Traduce TipoNotificacion <-> el texto del dominio tipo_notificacion.
	@Converter
	public static class TipoNotificacionConvertidor implements AttributeConverter<TipoNotificacion, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(TipoNotificacion atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public TipoNotificacion convertToEntityAttribute(String texto) {
			return texto == null ? null : TipoNotificacion.desdeValor(texto);
		}
	}

	// Traduce EstadoAnalisis <-> el texto del dominio estado_analisis.
	@Converter
	public static class EstadoAnalisisConvertidor implements AttributeConverter<EstadoAnalisis, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(EstadoAnalisis atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public EstadoAnalisis convertToEntityAttribute(String texto) {
			return texto == null ? null : EstadoAnalisis.desdeValor(texto);
		}
	}

	// Traduce EtapaAnalisis <-> el texto del dominio etapa_analisis.
	@Converter
	public static class EtapaAnalisisConvertidor implements AttributeConverter<EtapaAnalisis, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(EtapaAnalisis atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public EtapaAnalisis convertToEntityAttribute(String texto) {
			return texto == null ? null : EtapaAnalisis.desdeValor(texto);
		}
	}

	// Traduce NivelClasifAnalisis <-> el texto del dominio nivel_clasif_analisis.
	@Converter
	public static class NivelClasifAnalisisConvertidor implements AttributeConverter<NivelClasifAnalisis, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(NivelClasifAnalisis atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public NivelClasifAnalisis convertToEntityAttribute(String texto) {
			return texto == null ? null : NivelClasifAnalisis.desdeValor(texto);
		}
	}

	// Traduce OrigenSegundo <-> el texto del dominio origen_segundo.
	@Converter
	public static class OrigenSegundoConvertidor implements AttributeConverter<OrigenSegundo, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(OrigenSegundo atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public OrigenSegundo convertToEntityAttribute(String texto) {
			return texto == null ? null : OrigenSegundo.desdeValor(texto);
		}
	}

	// Traduce TipoConducta <-> el texto del dominio tipo_conducta. Es el conversor donde más importa dejar
	// pasar el null: la conducta de un segundo es null siempre que no se vio al espécimen o quedó dudosa.
	@Converter
	public static class TipoConductaConvertidor implements AttributeConverter<TipoConducta, String> {
		// De Java a la base: el texto del dominio, o null.
		@Override
		public String convertToDatabaseColumn(TipoConducta atributo) {
			return atributo == null ? null : atributo.valor();
		}

		// De la base a Java: la constante con ese texto, o excepción si no la conoce.
		@Override
		public TipoConducta convertToEntityAttribute(String texto) {
			return texto == null ? null : TipoConducta.desdeValor(texto);
		}
	}

}
