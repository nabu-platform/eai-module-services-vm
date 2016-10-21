package be.nabu.eai.module.services.vm.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.developer.MainController.PropertyUpdater;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.services.vm.VMServiceGUIManager;
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
	private Mapping mapping;

	public LinkPropertyUpdater(Link link, Mapping mapping) {
		this.link = link;
		this.mapping = mapping;		
	}
	
	@Override
	public Set<Property<?>> getSupportedProperties() {
		Set<Property<?>> properties = new LinkedHashSet<Property<?>>();
		// if the input is not fixed, explode that as well
		if (!link.isFixedValue()) {
			for (String indexed : explode(null, new ParsedPath(link.getFrom())).keySet()) {
				properties.add(new LinkIndexProperty(indexed, true));
			}
		}
		for (String indexed : explode(null, new ParsedPath(link.getTo())).keySet()) {
			properties.add(new LinkIndexProperty(indexed, false));
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
				values.add(new ValueImpl(property, getCurrentIndex((LinkIndexProperty) property)));
			}
		}
		values.add(new ValueImpl(OPTIONAL_PROPERTY, link.isOptional()));
		return values.toArray(new Value[0]);
	}

	@Override
	public boolean canUpdate(Property<?> property) {
		return true;
	}
	
	private String getCurrentIndex(LinkIndexProperty property) {
		if (property.isFrom()) {
			return explode(null, new ParsedPath(link.getFrom())).get(property.getActualName());
		}
		else {
			return explode(null, new ParsedPath(link.getTo())).get(property.getActualName());
		}
	}
	
	@Override
	public List<ValidationMessage> updateProperty(Property<?> property, Object value) {
		if (property instanceof LinkIndexProperty) {
			LinkIndexProperty linkProperty = (LinkIndexProperty) property;
			if (linkProperty.isFrom()) {
				ParsedPath from = new ParsedPath(link.getFrom());
				update(from, new ParsedPath(linkProperty.getActualName()), (String) value);
				link.setFrom(from.toString());
			}
			else {
				ParsedPath to = new ParsedPath(link.getTo());
				update(to, new ParsedPath(linkProperty.getActualName()), (String) value);
				link.setTo(to.toString());
			}
		}
		else if (property.equals(OPTIONAL_PROPERTY)) {
			link.setOptional(value instanceof Boolean && (Boolean) value);
		}
		else {
			throw new RuntimeException("Unsupported");
		}
		if (mapping != null) {
			if ((link.getFrom() != null && VMServiceGUIManager.hasIndexQuery(new ParsedPath(link.getFrom()))) || (link.getTo() != null && VMServiceGUIManager.hasIndexQuery(new ParsedPath(link.getTo())))) {
				mapping.addStyleClass("indexQueryLine");
			}
			else {
				mapping.removeStyleClass("indexQueryLine");
			}
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
		private String actualName;
		private boolean from;

		public LinkIndexProperty(String actualName, boolean from) {
			this.from = from;
			this.name = (from ? "From: " : "To: ") + (actualName.matches("(/|)result[a-f0-9]{32}.*") ? actualName.substring(39) : actualName);
			this.actualName = actualName;
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
			return object instanceof LinkIndexProperty && ((LinkIndexProperty) object).getActualName().equals(actualName) && ((LinkIndexProperty) object).from == from;
		}
		@Override
		public int hashCode() {
			return name.hashCode();
		}
		
		public String getActualName() {
			return actualName;
		}
		public boolean isFrom() {
			return from;
		}
		
	}

	@Override
	public boolean isMandatory(Property<?> property) {
		return false;
	}
}
