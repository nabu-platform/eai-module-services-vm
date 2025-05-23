/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.services.vm.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.MainController.PropertyUpdaterWithSource;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.services.vm.VMServiceGUIManager;
import be.nabu.eai.repository.api.Repository;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.Validator;

public class LinkPropertyUpdater implements PropertyUpdaterWithSource {

	private static final SimpleProperty<Boolean> OPTIONAL_PROPERTY = new SimpleProperty<Boolean>("optional", Boolean.class, true);
	private static final SimpleProperty<Boolean> PATCH_PROPERTY = new SimpleProperty<Boolean>("patch", Boolean.class, true);
	static {
		OPTIONAL_PROPERTY.setDescription("The value will only be applied if the target value is null, this is mainly to set default values");
		PATCH_PROPERTY.setDescription("The value will only be applied if the source value has an explicitly defined value");
	}
	private Link link;
	private Mapping mapping;
	private Repository repository;
	private String sourceId;

	public LinkPropertyUpdater(Link link, Mapping mapping, Repository repository, String sourceId) {
		this.link = link;
		this.mapping = mapping;
		this.repository = repository;
		this.sourceId = sourceId;		
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
		// the link get to can be null in case we map to the input of a service invoke
		// in that case we don't have indexes
		if (link.getTo() != null) {
			for (String indexed : explode(null, new ParsedPath(link.getTo())).keySet()) {
				properties.add(new LinkIndexProperty(indexed, false));
			}
		}
		properties.add(OPTIONAL_PROPERTY);
		properties.add(PATCH_PROPERTY);
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
			if (!property.equals(OPTIONAL_PROPERTY) && !property.equals(PATCH_PROPERTY)) {
				values.add(new ValueImpl(property, getCurrentIndex((LinkIndexProperty) property)));
			}
		}
		values.add(new ValueImpl(OPTIONAL_PROPERTY, link.isOptional()));
		values.add(new ValueImpl(PATCH_PROPERTY, link.isPatch()));
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
		else if (property.equals(PATCH_PROPERTY)) {
			link.setPatch(value instanceof Boolean && (Boolean) value);
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
		mapping.calculateLabels();
		MainController.getInstance().setChanged();
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

	@Override
	public String getSourceId() {
		return sourceId;
	}

	@Override
	public Repository getRepository() {
		return repository;
	}
}
