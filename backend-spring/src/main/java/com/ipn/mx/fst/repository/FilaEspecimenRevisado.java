// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// Un espécimen con revisión humana, de la consulta buscarRevisados de SegundoRepository. Spring llena cada método con la columna
// del mismo nombre (el alias "AS" del SQL).
//
// Sirve para marcar en el reporte qué especímenes tocó una persona y cuáles quedaron tal como los dejó el
// modelo. Es un dato de transparencia: al publicar resultados hay que poder decir qué parte de la medición
// es automática y qué parte está revisada a mano.
// No trae ningún conteo: solo identifica a los especímenes. Basta con que tengan UN segundo con origen
// distinto de 'maquina' para que aparezcan aquí (ver la condición de la consulta en SegundoRepository).
// Los tres campos juntos son lo que identifica a un espécimen, ya que no tiene nombre propio.
// Ver la explicación general de las proyecciones en FilaGrupo.
public interface FilaEspecimenRevisado {

	// La etiqueta del grupo.
	String getGrupo();

	// ordinal de la tanda: 1 = A, 2 = B, ...
	Number getOrdinal();

	// La posición en el cuadro, de 1 a 4.
	Number getCilindro();

}
