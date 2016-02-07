package be.nabu.eai.module.services.vm.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.developer.MainController.PropertyUpdater;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.Validator;

public class LinkPropertyUpdater implements PropertyUpdater {

	private static final SimpleProperty<Boolean> OPTIONAL_PROPERTY = new SimpleProperty<Boolean>("optional", Boolean.class, true);
	private Link link;

	public LinkPropertyUpdater(Link link) {
		this.link = link;		
	}
	
	@Override
	public Set<Property<?>> getSupportedProperties() {
		Set<Property<?>> properties = new LinkedHashSet<Property<?>>();
		// if the input is not fixed, explode that as well
		if (!link.isFixedValue()) {
			for (String indexed : explode("From: ", new ParsedPath(link.getFrom())).keySet()) {
				properties.add(new LinkIndexProperty(indexed));
			}
		}
		for (String indexed : explode("To: ", new ParsedPath(link.getTo())).keySet()) {
			properties.add(new LinkIndexProperty(indexed));
		}
		properties.add(OPTIONAL_PROPERTY);
		return properties;
	}
	
	private Map<String, String> explode(String parentPath, ParsedPath path) {
		Map<String, String> exploded = new LinkedHashMap<String, String>();
		String currentPath = (parentPath == null ? "" : parentPath + "/") + path.getName();
		if (path.getIndex() != null) {
			exploded.put(currentPath, path.getIndex());
		}
		if (path.getChildPath() != null) {
			exploded.putAll(explode(currentPath, path.getChildPath()));
		}
		return exploded;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Value<?>[] getValues() {
		List<Value<?>> values = new ArrayList<Value<?>>();
		for (Property<?> property : getSupportedProperties()) {
			if (!property.equals(OPTIONAL_PROPERTY)) {
				values.add(new ValueImpl(property, getCurrentIndex(property)));
			}
		}
		values.add(new ValueImpl(OPTIONAL_PROPERTY, link.isOptional()));
		return values.toArray(new Value[0]);
	}

	@Override
	public boolean canUpdate(Property<?> property) {
		return true;
	}
	
	private String getCurrentIndex(Property<?> property) {
		if (isFrom(property)) {
			return explode("From: ", new ParsedPath(link.getFrom())).get(property.getName());
		}
		else if (isTo(property)) {
			return explode("To: ", new ParsedPath(link.getTo())).get(property.getName());
		}
		throw new RuntimeException("Not supported");
	}
	
	private boolean isFrom(Property<?> property) {
		return property instanceof LinkIndexProperty && property.getName().startsWith("From: ");
	}
	private boolean isTo(Property<?> property) {
		return property instanceof LinkIndexProperty && property.getName().startsWith("To: ");
	}

	@Override
	public List<ValidationMessage> updateProperty(Property<?> property, Object value) {
		if (isFrom(property)) {
			ParsedPath from = new ParsedPath(link.getFrom());
			update(from, new ParsedPath(property.getName().substring("From: /".length())), (String) value);
			link.setFrom(from.toString());
		}
		else if (isTo(property)) {
			ParsedPath to = new ParsedPath(link.getTo());
			update(to, new ParsedPath(property.getName().substring("To: /".length())), (String) value);
			link.setTo(to.toString());
		}
		else if (property.equals(OPTIONAL_PROPERTY)) {
			link.setOptional(value instanceof Boolean && (Boolean) value);
		}
		else {
			throw new RuntimeException("Unsupported");
		}
		return new ArrayList<ValidationMessage>();
	}
	
	private void update(ParsedPath fullPath, ParsedPath indexPath, String index) {
		if (fullPath.getName().equals(indexPath.getName())) {
			if (indexPath.getChildPath() == null) {
				fullPath.setIndex(index);
				fullPath.toString();
			}
			else {
				update(fullPath.getChildPath(), indexPath.getChildPath(), index);
			}
		}
		else {
			throw new IllegalArgumentException("Can't update the path, the reference path does not match");
		}
	}

	private class LinkIndexProperty implements Property<String> {

		private String name;

		public LinkIndexProperty(String name) {
			this.name = name;
		}
		@Override
		public String getName() {
			return name;
		}

		@Override
		public Validator<String> getValidator() {
			return null;
		}

		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
		@Override
		public boolean equals(Object object) {
			return object instanceof LinkIndexProperty && ((LinkIndexProperty) object).getName().equals(name);
		}
		@Override
		public int hashCode() {
			return name.hashCode();
		}
		
	}

	@Override
	public boolean isMandatory(Property<?> property) {
		return false;
	}
}
