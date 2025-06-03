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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.event.EventHandler;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.MainController.PropertyUpdater;
import be.nabu.eai.developer.MainController.PropertyUpdaterWithSource;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.developer.managers.util.EnumeratedSimpleProperty;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.repository.api.Repository;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.drag.TreeDragDrop;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeConverterFactory;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.MarshalException;
import be.nabu.libs.types.api.Marshallable;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeConverter;
import be.nabu.libs.types.api.Unmarshallable;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class FixedValue {
	
	public static final String SHOW_HIDDEN_FIXED = "be.nabu.eai.developer.showHiddenFixedValues";
	
	private Link link;
	private TreeCell<Element<?>> cell;
	private ImageView image;

	private Repository repository;

	private String sourceId;
	
	public static void allowFixedValue(final MainController controller, final java.util.Map<Link, FixedValue> fixedValues, final Tree<Step> serviceTree, final Tree<Element<?>> tree, Repository repository, String sourceId) {
		final SimpleTypeWrapper simpleTypeWrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
		final TypeConverter typeConverter = TypeConverterFactory.getInstance().getConverter();
		// the tree can be null for failed invokes (service does not exist etc)
		if (tree != null) {
			tree.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					final TreeCell<Element<?>> selected = tree.getSelectionModel().getSelectedItem();
					// it must be unmarshallable and it _can_ be a list, if it's a list, you will get the opportunity to set the indexes
					if (selected != null && (selected.getItem().itemProperty().get().getType() instanceof Unmarshallable 
							|| typeConverter.canConvert(new BaseTypeInstance(simpleTypeWrapper.wrap(String.class)), selected.getItem().itemProperty().get()))
							|| (selected.getItem().itemProperty().get().getType() instanceof BeanType && ((BeanType<?>) selected.getItem().itemProperty().get().getType()).getBeanClass().equals(Object.class))) {
						if (event.getClickCount() == 2) {
							PropertyUpdater updater = new FixedValuePropertyUpdater(selected.getItem().itemProperty().get(), fixedValues, serviceTree, tree, repository, sourceId);
							EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Update Fixed Value", null, true, controller.getStageFor(sourceId), true);
						}
					}
				}
			});
		}
	}
	
	public FixedValue(MainController controller, TreeCell<Element<?>> cell, Link link, Repository repository, String sourceId) {
		this.cell = cell;
		this.link = link;
		this.repository = repository;
		this.sourceId = sourceId;
		draw(controller, link);
	}
	
	private void draw(final MainController controller, final Link link) {
		image = MainController.loadGraphic("fixed-value.png");
		image.setManaged(false);
		// allow key events
		image.setFocusTraversable(true);
		((Pane) cell.getTree().getParent()).getChildren().add(image);
		image.layoutXProperty().bind(cell.leftAnchorXProperty().subtract(10));
		// image is 16 pixels, we want to center it
		image.layoutYProperty().bind(cell.leftAnchorYProperty().subtract(8));
		// make invisible if it is not in scope
		if (Boolean.FALSE.toString().equals(System.getProperty(SHOW_HIDDEN_FIXED, "true"))) {
			image.visibleProperty().bind(cell.getNode().visibleProperty());
		}
		image.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				cell.show();
				image.requestFocus();
				controller.showProperties(new LinkPropertyUpdater(link, null, repository, sourceId));
			}
		});
		new CustomTooltip(link.getFrom()).install(image);
//		Tooltip.install(image, new Tooltip(link.getFrom()));
	}
	
	public void remove() {
		((Pane) image.getParent()).getChildren().remove(image);
	}

	public ImageView getImage() {
		return image;
	}

	public Link getLink() {
		return link;
	}

	public TreeCell<Element<?>> getCell() {
		return cell;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static class FixedValuePropertyUpdater implements PropertyUpdaterWithSource {
		
		private SimpleProperty<?> property;
		private Type type;
		private java.util.Map<Link, FixedValue> fixedValues;
		private Tree<Element<?>> tree;
		private Tree<Step> serviceTree;
		private Element<?> element;
		private String sourceId;
		private Repository repository;
		
		public FixedValuePropertyUpdater(Element<?> element, java.util.Map<Link, FixedValue> fixedValues, Tree<Step> serviceTree, Tree<Element<?>> tree, Repository repository, String sourceId) {
			this.element = element;
			this.repository = repository;
			this.sourceId = sourceId;
			this.type = element.getType();
			this.fixedValues = fixedValues;
			this.serviceTree = serviceTree;
			this.tree = tree;
			List<?> enumerations = (List<?>) ValueUtils.getValue(new EnumerationProperty(), type.getProperties());
			if (enumerations != null && !enumerations.isEmpty()) {
				this.property = new EnumeratedSimpleProperty("Fixed Value", type instanceof SimpleType ? ((SimpleType) type).getInstanceClass() : String.class, false);
				((EnumeratedSimpleProperty) this.property).addEnumeration(enumerations);
			}
			else {
				this.property = new SimpleProperty("Fixed Value", type instanceof SimpleType ? ((SimpleType) type).getInstanceClass() : String.class, false);
			}
			this.property.setEvaluatable(true);
			
			this.property.getAdditional().addAll(Arrays.asList(element.getProperties()));
		}
		
		@Override
		public Set<Property<?>> getSupportedProperties() {
			return new HashSet<Property<?>>(Arrays.asList(property));
		}
		@Override
		public Value<?>[] getValues() {
			String value = null;
			for (FixedValue fixed : fixedValues.values()) {
				if (fixed.getCell().equals(tree.getSelectionModel().getSelectedItem())) {
					value = fixed.getLink().getFrom();
					break;
				}
			}
			return new Value [] { new ValueImpl(property, value) };
		}
		@Override
		public boolean canUpdate(Property<?> property) {
			return true;
		}
		@Override
		public List<ValidationMessage> updateProperty(Property<?> property, Object object) {
			FixedValue existing = null;
			for (FixedValue fixed : fixedValues.values()) {
				if (fixed.getCell().equals(tree.getSelectionModel().getSelectedItem())) {
					existing = fixed;
					break;
				}
			}
			// if there is a fixed value, we need to remove it
			if (object == null) {
				if (existing != null) {
					fixedValues.remove(existing.getLink());
					existing.getLink().getParent().getChildren().remove(existing.getLink());
					((Pane) existing.getImage().getParent()).getChildren().remove(existing.getImage());
					MainController.getInstance().setChanged();
				}
			}
			else {
				try {
					final SimpleTypeWrapper simpleTypeWrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
					final TypeConverter typeConverter = TypeConverterFactory.getInstance().getConverter();
					TreeCell<Element<?>> selected = tree.getSelectionModel().getSelectedItem();
					
					String value;
					// if a string comes in, it is possible that we have a calculation on our hands so even though the type is integer, it could contain "=1+2"
					if (object instanceof String) {
						value = (String) object;
					}
					else if (type instanceof Marshallable) {
						value = ((Marshallable) type).marshal(object, element.getProperties());
					}
					else {
						value = typeConverter.convert(object, element, new BaseTypeInstance(simpleTypeWrapper.wrap(String.class)));
					}
					// only trigger change if you actually updated the value
					if (existing == null || !value.equals(existing.getLink().getFrom())) {
						MainController.getInstance().setChanged();
						if (existing != null) {
							existing.getLink().setFrom(value);
						}
						else {
							Link link = new Link();
							link.setFixedValue(true);
							link.setFrom(value);
							
							ParsedPath path = new ParsedPath(TreeDragDrop.getPath(selected.getItem()));
							if (tree.get("invoke") != null) {
								Invoke invoke = (Invoke) tree.get("invoke");
								// the first entry must be input
								if (!path.getName().equals("input")) {
									throw new IllegalArgumentException("Can't set it here");
								}
								DropLinkListener.setDefaultIndexes(path.getChildPath(), tree.rootProperty().get(), true);
								link.setTo(path.getChildPath().toString());
								invoke.getChildren().add(link);
								link.setParent(invoke);
							}
							else {
								if (!path.getName().equals("pipeline")) {
									throw new IllegalArgumentException("Can't set it here");
								}
								DropLinkListener.setDefaultIndexes(path.getChildPath(), tree.rootProperty().get(), true);
								link.setTo(path.getChildPath().toString());
								link.setParent((Map) serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get());
								((Map) serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get()).getChildren().add(link);
							}
							fixedValues.put(link, new FixedValue(MainController.getInstance(), selected, link, repository, sourceId));
						}
					}
				}
				catch (MarshalException e) {
					MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "The value '" + object + "' is incorrect for this field type"));
				}
			}
			return null;
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
}
