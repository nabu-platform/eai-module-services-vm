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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.MainController.PropertyUpdater;
import be.nabu.eai.developer.MainController.PropertyUpdaterWithSource;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.developer.managers.util.ElementLineConnectListener;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.managers.util.EnumeratedSimpleProperty;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.ElementClipboardHandler;
import be.nabu.eai.developer.util.ElementSelectionListener;
import be.nabu.eai.developer.util.ElementTreeItem;
import be.nabu.eai.module.services.vm.RepositoryExecutorProvider;
import be.nabu.eai.module.types.structure.StructureGUIManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.jfx.control.tree.drag.TreeDragDrop;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.vm.api.ExecutorProvider;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.RootElement;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.ValidationMessage;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InvokeWrapper {
	
	private Invoke invoke;
	private Pane target;
	private java.util.Map<Link, Mapping> mappings;
	private Tree<Step> serviceTree;
	private VMService service;
	private VMServiceController serviceController;
	private Tree<Element<?>> input, output;
	private MainController controller;
	private ExecutorProvider executorProvider;
	private ReadOnlyBooleanProperty lock;
	private Repository repository;
	private String sourceId;

	public InvokeWrapper(MainController controller, Invoke invoke, Pane target, VMService service, VMServiceController serviceController, Tree<Step> serviceTree, java.util.Map<Link, Mapping> mappings, ReadOnlyBooleanProperty lock, Repository repository, String sourceId) {
		this.controller = controller;
		this.invoke = invoke;
		this.target = target;
		this.service = service;
		this.serviceTree = serviceTree;
		this.serviceController = serviceController;
		this.mappings = mappings;
		this.lock = lock;
		this.repository = repository;
		this.sourceId = sourceId;
		this.executorProvider = new RepositoryExecutorProvider(controller.getRepository());
	}
	
	public Pane getComponent() {
		VBox vbox = new VBox();
		// use an anchorpane, because if you set the vbox to unmanaged, things go...wrong
		final AnchorPane pane = new AnchorPane();
		EventHandler<KeyEvent> keyHandler = new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				// only allow delete if it is visually selected
				if (event.getCode() == KeyCode.DELETE && vbox.getStyleClass().contains("selectedInvoke")) {
					// the input is mapped inside the invoke itself so from the object perspective they don't have to be specifically removed
					// however we do need to remove the lines that were drawn
					for (Step child : invoke.getChildren()) {
						if (child instanceof Link) {
							Link link = (Link) child;
							Mapping mapping = mappings.get(link);
							if (mapping != null) {
								mappings.remove(link);
								mapping.remove();
							}
						}
					}
					// remove anyone who has mapped an output from this invoke
					removeInGroup(invoke.getParent());
					invoke.getParent().getChildren().remove(invoke);
					((Pane) pane.getParent()).getChildren().remove(pane);
					event.consume();
					MainController.getInstance().setChanged();
				}
				else if (event.getCode() == KeyCode.L && event.isControlDown()) {
					Tree<Entry> tree = MainController.getInstance().getTree();
					TreeItem<Entry> resolve = tree.resolve(invoke.getServiceId().replace(".", "/"));
					if (resolve != null) {
						TreeCell<Entry> treeCell = tree.getTreeCell(resolve);
						// if you _first_ do show and _then_ select, it doesn't work in the oddest way, the tree does jump open, the selection is correct, everything works...except for the autoscroll
						// the autoscroll calculates the positions just fine, the only thing that _doesn't_ work is setVvalue on the scrollbar, it is simply ignored
						treeCell.select();
						treeCell.show();
						tree.autoscroll(true);
						MainController.getInstance().switchToRepository();
					}
					event.consume();
				}
				// copy the service
				else if (event.getCode() == KeyCode.C && event.isControlDown()) {
					MainController.copy(invoke.getServiceId());
				}
			}
		};
		pane.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
		
		// the input & output should not be scrollable but should resize on demand
		final Service service = invoke.getService(controller.getRepository().getServiceContext());
		
		pane.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				pane.requestFocus();
				SimpleProperty<Integer> invocationProperty = new SimpleProperty<Integer>("invocationOrder", Integer.class, true);
				EnumeratedSimpleProperty<String> targetProperty = new EnumeratedSimpleProperty<String>("target", String.class, false);
				SimpleProperty<Boolean> asynchronousProperty = new SimpleProperty<Boolean>("asynchronous", Boolean.class, false);
				SimpleProperty<Boolean> recacheProperty = new SimpleProperty<Boolean>("recache", Boolean.class, false);
				recacheProperty.setTitle("If this service is cached and you set boolean to true, upon invocation any existing cache is ignored, the service is explicitly executed and the result is cached. This effectively refreshes this particular instance of the cache");
				targetProperty.addAll(InvokeWrapper.this.service.getExecutorProvider().getTargets().toArray(new String[0]));
//				targetProperty.addAll("$self", "$any", "$all", "$other");
				HashSet<Property<?>> hashSet = new HashSet<Property<?>>(Arrays.asList(invocationProperty, targetProperty, recacheProperty, asynchronousProperty));
				
				List<Property<?>> targetProperties = new ArrayList<Property<?>>();
				if (invoke.getTarget() != null && executorProvider != null) {
					targetProperties.addAll(executorProvider.getTargetProperties(invoke.getTarget()));
					hashSet.addAll(targetProperties);
				}
				
				PropertyUpdater updater = new PropertyUpdaterWithSource() {
					@Override
					public Set<Property<?>> getSupportedProperties() {
						return hashSet;
					}
					@SuppressWarnings({ "unchecked", "rawtypes" })
					@Override
					public Value<?>[] getValues() {
						List<Value<?>> list = new ArrayList<Value<?>>(Arrays.asList(new Value<?> [] { 
							new ValueImpl<Integer>(invocationProperty, invoke.getInvocationOrder()),
							new ValueImpl<Boolean>(recacheProperty, invoke.isRecache()),
							new ValueImpl<String>(targetProperty, invoke.getTarget()),
							new ValueImpl<Boolean>(asynchronousProperty, invoke.isAsynchronous() || (invoke.getTarget() != null && executorProvider != null && executorProvider.isAsynchronous(invoke.getTarget())))
						}));
						java.util.Map<String, String> values = invoke.getTargetProperties();
						if (values != null) {
							for (Property<?> property : targetProperties) {
								Object value = values.get(property.getName());
								if (value != null) {
									if (!String.class.isAssignableFrom(property.getValueClass()) && !((String) value).startsWith("=")) {
										value = ConverterFactory.getInstance().getConverter().convert(value, property.getValueClass());
									}
									list.add(new ValueImpl(property, value));
								}
							}
						}
						return list.toArray(new Value<?>[list.size()]);
					}
					@Override
					public boolean canUpdate(Property<?> property) {
						// can not update the value of asynchronous if the target does not support synchronous
						if (property.equals(asynchronousProperty) && invoke.getTarget() != null && executorProvider != null && executorProvider.isAsynchronous(invoke.getTarget())) {
							return false;
						}
						return true;
					}
					@Override
					public List<ValidationMessage> updateProperty(Property<?> property, Object value) {
						if (value instanceof Integer && property.equals(invocationProperty)) {
							invoke.setInvocationOrder((Integer) value);
						}
						else if (property.equals(recacheProperty)) {
							invoke.setRecache(value instanceof Boolean && (Boolean) value);
						}
						else if (property.equals(asynchronousProperty)) {
							invoke.setAsynchronous(value instanceof Boolean && (Boolean) value);
							updateOutput(service);
						}
						else if (property.equals(targetProperty)) {
							invoke.setTarget(value == null ? null : value.toString());
							updateOutput(service);
							
							hashSet.removeAll(targetProperties);
							targetProperties.clear();
							invoke.setTargetProperties(null);
							if (invoke.getTarget() != null && executorProvider != null) {
								targetProperties.addAll(executorProvider.getTargetProperties(invoke.getTarget()));
								hashSet.addAll(targetProperties);
							}
						}
						else {
							if (invoke.getTargetProperties() == null) {
								invoke.setTargetProperties(new HashMap<String, String>());
							}
							if (value == null) {
								invoke.getTargetProperties().remove(property.getName());
							}
							else {
								if (!String.class.isAssignableFrom(property.getValueClass()) && value != null && !(value instanceof String)) {
									value = ConverterFactory.getInstance().getConverter().convert(value, String.class);
								}
								invoke.getTargetProperties().put(property.getName(), (String) value);
							}
						}
						MainController.getInstance().setChanged();
						return invoke.getParent() instanceof Map ? ((Map) invoke.getParent()).calculateInvocationOrder() : null;
					}
					@Override
					public boolean isMandatory(Property<?> property) {
						return true;
					}
					@Override
					public String getSourceId() {
						return sourceId;
					}
					@Override
					public Repository getRepository() {
						return repository;
					}
				};
				controller.showProperties(updater);
			}
		});
		
		vbox.getStyleClass().add("invokeWrapper");
		HBox name = new HBox();
		name.setAlignment(Pos.CENTER_LEFT);
		name.getStyleClass().add("invokeName");
		VBox mainNameBox = new VBox();
		Label nameLabel = new Label(invoke.getServiceId());
		nameLabel.getStyleClass().add("invokeServiceName");
		mainNameBox.getChildren().add(nameLabel);
		Label subscript = null;
		Entry entry = repository.getEntry(invoke.getServiceId());
		if (entry != null) {
			ImageView graphic = controller.getGUIManager(entry.getNode().getArtifactClass()).getGraphic();
			new CustomTooltip("Invoke order: " + invoke.getInvocationOrder()).install(graphic);
			name.getChildren().add(MainController.wrapInFixed(graphic, 25, 25));
			String comment = VMServiceUtils.templateServiceComment(invoke);
			if (comment != null) {
//				nameLabel.setText(comment);
				nameLabel.setText(comment);
				nameLabel.setWrapText(true);
				subscript = new Label(invoke.getServiceId());
				subscript.getStyleClass().add("invokeSubscript");
				mainNameBox.getChildren().add(subscript);
			}
		}
		
//		Label invokeLevelLabel = new Label("[" + invoke.getInvocationOrder() + "]");
//		invokeLevelLabel.getStyleClass().add("invokeOrder");
//		name.getChildren().addAll(invokeLevelLabel, mainNameBox);
		name.getChildren().add(mainNameBox);
		
		String description = service != null ? service.getDescription() : null;
		if ((description == null || description.trim().isEmpty()) && entry != null) {
			description = entry.getNode().getDescription();
		}
		// add more info about the service if available
		if (description != null && !description.trim().isEmpty()) {
			Node loadGraphic = MainController.getInfoIcon();
			CustomTooltip customTooltip = new CustomTooltip(description);
			customTooltip.install(loadGraphic);
			customTooltip.setMaxWidth(400d);
			nameLabel.setGraphic(loadGraphic);
			// if it's on the right, it is next to the button making it harder to see
//			nameLabel.setContentDisplay(ContentDisplay.RIGHT);
		}
		
		Label goToLabel = new Label();
		goToLabel.getStyleClass().add("invokeServiceGoto");
		goToLabel.setGraphic(MainController.loadFixedSizeGraphic("right-chevron.png", 12));
		goToLabel.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				MainController.getInstance().open(invoke.getServiceId());
			}
		});
		new CustomTooltip("View Service").install(goToLabel);
		HBox spacer = new HBox();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		name.getChildren().addAll(spacer, goToLabel);
		vbox.getChildren().add(name);
		
		ChangeListener<Boolean> toFrontListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				if (arg2 != null && arg2) {
					pane.toFront();
					vbox.getStyleClass().add("selectedInvoke");
				}
				else {
					vbox.getStyleClass().remove("selectedInvoke");
				}
			}
		};
		pane.focusedProperty().addListener(toFrontListener);
		
		vbox.getStyleClass().add("service");
		if (service != null) {
			input = new Tree<Element<?>>(new ElementMarshallable(), null, StructureGUIManager.newCellDescriptor());
			EAIDeveloperUtils.addElementExpansionHandler(input);
			input.setClipboardHandler(new ElementClipboardHandler(input, false));
			input.setReadOnly(true);
			input.set("invoke", invoke);
			input.rootProperty().set(new ElementTreeItem(new RootElement(service.getServiceInterface().getInputDefinition(), "input"), null, false, false));
			input.getTreeCell(input.rootProperty().get()).expandedProperty().set(false);
			TreeDragDrop.makeDroppable(input, new DropLinkListener(controller, mappings, this.service, serviceController, serviceTree, lock, repository, sourceId));
			input.getRootCell().getNode().getStyleClass().add("invokeTree");
			
			output = new Tree<Element<?>>(new ElementMarshallable(), null, StructureGUIManager.newCellDescriptor());
			EAIDeveloperUtils.addElementExpansionHandler(output);
			output.setClipboardHandler(new ElementClipboardHandler(output, false));
			output.setReadOnly(true);
			
			updateOutput(service);

			ElementSelectionListener elementSelectionListener = new ElementSelectionListener(controller, false);
			elementSelectionListener.setActualId(sourceId);
			input.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
			output.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
//			if (executorProvider.isBatch(invoke.getTarget())) {
//				output.rootProperty().set(new ElementTreeItem(new RootElement(ExecutorProvider.getBatchOutput(service), "output"), null, false, false));
//			}
//			// for asynchronous invoke there is no return value
//			else if (!invoke.isAsynchronous()) {
//				output.rootProperty().set(new ElementTreeItem(new RootElement(service.getServiceInterface().getOutputDefinition(), "output"), null, false, false));
//			}
//			// so we just put an empty structure in that case
//			else {
//				output.rootProperty().set(new ElementTreeItem(new RootElement(new Structure(), "output"), null, false, false));
//			}
			output.set("invoke", invoke);
			output.getTreeCell(output.rootProperty().get()).expandedProperty().set(false);
			output.getRootCell().getNode().getStyleClass().add("invokeTree");
			TreeDragDrop.makeDraggable(output, new ElementLineConnectListener(target));
		
			HBox iface = new HBox();
			iface.getStyleClass().add("interface");
			iface.getStyleClass().add("interfaceContainer");
			iface.getChildren().addAll(input, output);
			vbox.getChildren().add(iface);
			vbox.getStyleClass().add("existent");

			input.resize();
			output.resize();
			
			input.getStyleClass().add("treeContainer");
			output.getStyleClass().add("treeContainer");
			
			// the initial resize just won't work...
			input.setPrefWidth(100);
			output.setPrefWidth(100);
//			nameLabel.widthProperty().addListener(new ChangeListener<Number>() {
//				@Override
//				public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
//					if (arg2.intValue() > 200) {
//						iface.setMinWidth(arg2.doubleValue());
//						if (iface.getPrefWidth() < arg2.doubleValue()) {
//							iface.setPrefWidth(arg2.doubleValue());
//						}
//						nameLabel.widthProperty().removeListener(this);
//					}
//				}
//			});
			
			name.minWidthProperty().bind(iface.widthProperty());
			iface.minWidthProperty().bind(input.widthProperty().add(output.widthProperty()));
			
			// can not find a proper dynamic way of recalculating the holder without visual artifacts 			
//			input.boundsInLocalProperty().addListener(new ChangeListener<Bounds>() {
//				@Override
//				public void changed(ObservableValue<? extends Bounds> arg0, Bounds arg1, Bounds arg2) {
//					setClasses(iface, input, output, nameLabel, invokeLevelLabel);
//				}
//			});
//			output.boundsInLocalProperty().addListener(new ChangeListener<Bounds>() {
//				@Override
//				public void changed(ObservableValue<? extends Bounds> arg0, Bounds arg1, Bounds arg2) {
//					setClasses(iface, input, output, nameLabel, invokeLevelLabel);
//				}
//			});
			// does not work
//			vbox.prefWidthProperty().bind(new ComparableAmountListener<Double>(name.widthProperty(), iface.widthProperty(), input.getRootCell().getNode().widthProperty().add(output.getRootCell().getNode().widthProperty()), nameLabel.widthProperty().add(invokeLevelLabel.widthProperty())).maxProperty());
		}
		else {
			vbox.getStyleClass().add("nonExistent");
		}
		pane.getChildren().add(vbox);
//		pane.setManaged(false);
		pane.setLayoutX(invoke.getX());
		pane.setLayoutY(invoke.getY());
		return pane;
	}
	
	@SuppressWarnings("unused")
	private void setClasses(HBox iface, Tree<Element<?>> input, Tree<Element<?>> output, Label nameLabel, Label invokeLevelLabel) {
		if (input.getRootCell().getNode().getBoundsInLocal().getWidth() + output.getRootCell().getNode().getBoundsInLocal().getWidth() > nameLabel.getBoundsInLocal().getWidth() + invokeLevelLabel.getBoundsInLocal().getWidth()) {
			iface.getStyleClass().remove("interfaceContainer");
			input.getStyleClass().add("treeLeftContainer");
			output.getStyleClass().add("treeRightContainer");
		}
		else {
			iface.getStyleClass().add("interfaceContainer");
			input.getStyleClass().remove("treeLeftContainer");
			output.getStyleClass().remove("treeRightContainer");
		}
	}
	
	private void updateOutput(Service service) {
		// no output for asynchronous invokes
		if (invoke.isAsynchronous() || executorProvider.isAsynchronous(invoke.getTarget())) {
			output.rootProperty().set(new ElementTreeItem(new RootElement(new Structure(), "output"), null, false, false));
		}
		// batch output for batch invokes
		else if (executorProvider.isBatch(invoke.getTarget())) {
			output.rootProperty().set(new ElementTreeItem(new RootElement(ExecutorProvider.getBatchOutput(service), "output"), null, false, false));
		}
		// otherwise just the regular output
		else {
			output.rootProperty().set(new ElementTreeItem(new RootElement(service.getServiceInterface().getOutputDefinition(), "output"), null, false, false));
		}
		output.refresh();
	}
	
	private void removeInGroup(StepGroup group) {
		List<Step> children = group.getChildren();
		for (int i = children.size() - 1; i >= 0; i--) {
			Step child = children.get(i);
			if (child instanceof Link) {
				Link link = (Link) child;
				if (link.getFrom().startsWith(invoke.getResultName() + "/")) {
					group.getChildren().remove(i);
					Mapping mapping = mappings.get(link);
					if (mapping != null) {
						mappings.remove(link);
						mapping.remove();
					}
				}
			}
			else if (child instanceof Invoke) {
				removeInGroup((Invoke) child);
			}
		}
	}
	
	public Tree<Element<?>> getInput() {
		return input;
	}

	public Tree<Element<?>> getOutput() {
		return output;
	}
}
