package net.foxgenesis.customjail.database;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import net.foxgenesis.customjail.util.CustomTime;

@Converter(autoApply = true)
public class CustomTimeConverter implements AttributeConverter<CustomTime, String> {

	@Override
	public String convertToDatabaseColumn(CustomTime attribute) {
		if (attribute == null)
			return null;
		return attribute.toString();
	}

	@Override
	public CustomTime convertToEntityAttribute(String dbData) {
		return new CustomTime(dbData);
	}
}