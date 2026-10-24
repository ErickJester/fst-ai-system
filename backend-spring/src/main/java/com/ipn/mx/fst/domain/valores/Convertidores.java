package com.ipn.mx.fst.domain.valores;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Un conversor JPA por enum: guarda y lee en la base el texto EXACTO del dominio (valor()), no el nombre de la constante.
// Cada uno deja pasar el null (Segundo.conducta es null cuando no se ve al espécimen) y, si la base trajera un texto que
// el enum no conoce, desdeValor lanza una IllegalArgumentException con el dominio y el texto. Se usan con
// @Convert(converter = Convertidores.XxxConvertidor.class) en cada campo; la columna conserva su columnDefinition con
// el nombre del dominio, que es lo que necesita ddl-auto=validate.
public final class Convertidores {

	private Convertidores() {
	}

	@Converter
	public static class TipoGrupoConvertidor implements AttributeConverter<TipoGrupo, String> {
		@Override
		public String convertToDatabaseColumn(TipoGrupo atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public TipoGrupo convertToEntityAttribute(String texto) {
			return texto == null ? null : TipoGrupo.desdeValor(texto);
		}
	}

	@Converter
	public static class SesionVideoConvertidor implements AttributeConverter<SesionVideo, String> {
		@Override
		public String convertToDatabaseColumn(SesionVideo atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public SesionVideo convertToEntityAttribute(String texto) {
			return texto == null ? null : SesionVideo.desdeValor(texto);
		}
	}

	@Converter
	public static class FormatoReporteConvertidor implements AttributeConverter<FormatoReporte, String> {
		@Override
		public String convertToDatabaseColumn(FormatoReporte atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public FormatoReporte convertToEntityAttribute(String texto) {
			return texto == null ? null : FormatoReporte.desdeValor(texto);
		}
	}

	@Converter
	public static class TipoNotificacionConvertidor implements AttributeConverter<TipoNotificacion, String> {
		@Override
		public String convertToDatabaseColumn(TipoNotificacion atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public TipoNotificacion convertToEntityAttribute(String texto) {
			return texto == null ? null : TipoNotificacion.desdeValor(texto);
		}
	}

	@Converter
	public static class EstadoAnalisisConvertidor implements AttributeConverter<EstadoAnalisis, String> {
		@Override
		public String convertToDatabaseColumn(EstadoAnalisis atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public EstadoAnalisis convertToEntityAttribute(String texto) {
			return texto == null ? null : EstadoAnalisis.desdeValor(texto);
		}
	}

	@Converter
	public static class EtapaAnalisisConvertidor implements AttributeConverter<EtapaAnalisis, String> {
		@Override
		public String convertToDatabaseColumn(EtapaAnalisis atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public EtapaAnalisis convertToEntityAttribute(String texto) {
			return texto == null ? null : EtapaAnalisis.desdeValor(texto);
		}
	}

	@Converter
	public static class NivelClasifAnalisisConvertidor implements AttributeConverter<NivelClasifAnalisis, String> {
		@Override
		public String convertToDatabaseColumn(NivelClasifAnalisis atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public NivelClasifAnalisis convertToEntityAttribute(String texto) {
			return texto == null ? null : NivelClasifAnalisis.desdeValor(texto);
		}
	}

	@Converter
	public static class OrigenSegundoConvertidor implements AttributeConverter<OrigenSegundo, String> {
		@Override
		public String convertToDatabaseColumn(OrigenSegundo atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public OrigenSegundo convertToEntityAttribute(String texto) {
			return texto == null ? null : OrigenSegundo.desdeValor(texto);
		}
	}

	@Converter
	public static class TipoConductaConvertidor implements AttributeConverter<TipoConducta, String> {
		@Override
		public String convertToDatabaseColumn(TipoConducta atributo) {
			return atributo == null ? null : atributo.valor();
		}

		@Override
		public TipoConducta convertToEntityAttribute(String texto) {
			return texto == null ? null : TipoConducta.desdeValor(texto);
		}
	}

}
