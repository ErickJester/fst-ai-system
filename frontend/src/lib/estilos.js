// Estilos en línea que se repiten entre pantallas (mockups v2).

// Texto atenuado: 60 % y 70 % del color del texto.
export const MUT60 = 'color-mix(in srgb,var(--color-text) 60%,transparent)'
export const MUT70 = 'color-mix(in srgb,var(--color-text) 70%,transparent)'

// Botón con el texto pegado a la izquierda.
export const btnLeft = { justifyContent: 'flex-start' }

// Nota pequeña y atenuada bajo un campo o junto a un control.
export const mutedSub = { fontSize: 12, color: MUT60 }

// Párrafo introductorio de las tarjetas de acceso (2h, 2i).
export const textoTarjeta = { margin: '0 0 22px', fontSize: 13, lineHeight: 1.6, color: MUT70, textWrap: 'pretty' }
