// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// Una fila de GrupoRepository.buscarGrupos: un grupo, con el ordinal que tendría su siguiente tanda.
//
// Qué es una PROYECCIÓN basada en interfaz: una interfaz con puros getters que Spring Data rellena con las
// columnas de una consulta. Nadie escribe su implementación; Spring crea un objeto al vuelo que responde a
// cada getter con la columna cuyo alias coincide con el nombre del método, quitándole el "get" y bajando
// la primera letra: getIdGrupo() ← el alias "idGrupo".
// De ahí que los alias del SQL vayan entre comillas dobles: sin ellas PostgreSQL los pasaría a minúsculas
// ("idgrupo") y dejarían de casar con el nombre del método.
//
// Por qué no devolver entidades Grupo: la consulta trae una columna, siguienteOrdinal, que no existe en
// ninguna tabla. Una entidad solo puede llevar lo que tiene la tabla; una proyección lleva exactamente lo
// que la pantalla necesita. Además es de solo lectura, así que nadie puede modificarla por descuido y
// provocar un UPDATE sin querer.
public interface FilaGrupo {

	// Para saber a qué experimento pertenece la fila, ya que la consulta trae los grupos de varios a la vez.
	Integer getIdExperimento();

	Integer getIdGrupo();

	// El nombre que le puso el investigador ("Control", "Fluoxetina 10 mg").
	String getEtiqueta();

	// El tipo como TEXTO crudo de la base ('control', 'referencia'...), no como el enum TipoGrupo:
	// los conversores de JPA trabajan sobre entidades, y una proyección se salta esa maquinaria.
	// Para compararlo hay método: TipoGrupo.CONTROL.es(fila.getTipo()), que es justo para lo que existe
	// el método es(...) de ValorDeBase.
	String getTipo();

	String getTratamiento();

	// max(ordinal) + 1, o 1 si el grupo todavía no tiene tandas
	//
	// Number y no Short ni Integer: el tipo exacto que devuelve PostgreSQL para una expresión calculada
	// depende del motor y del controlador. Pedir Number (la clase padre de todos los numéricos de Java)
	// acepta cualquiera de ellos, y quien lo use llama a intValue() o shortValue().
	Number getSiguienteOrdinal();

}
