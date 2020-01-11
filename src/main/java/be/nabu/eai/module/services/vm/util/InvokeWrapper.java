package be.nabu.eai.module.services.vm.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.MainController.PropertyUpdater;
import be.nabu.eai.developer.MainController.PropertyUpdaterWithSource;
import be.nabu.eai.developer.managers.util.ElementLineConnectListener;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.managers.util.EnumeratedSimpleProperty;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.ElementClipboardHandler;
import be.nabu.eai.developer.util.ElementTreeItem;
import be.nabu.eai.module.services.vm.RepositoryExecutorProvider;
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
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.api.ExecutorProvider;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.RootElement;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.ValidationMessage;

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
		// use an anchorpane, because if you set the vbox to unmanaged, things go...wrong
		final AnchorPane pane = new AnchorPane();
		pane.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getCode() == KeyCode.DELETE) {
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
						treeCell.show();
						treeCell.select();
						tree.autoscroll();
						MainController.getInstance().getStage().requestFocus();
						tree.requestFocus();
					}
					event.consume();
				}
			}
		});
		
		// the input & output should not be scrollable but should resize on demand
		final Service service = invoke.getService(controller.getRepository().getServiceContext());
		
		ChangeListener<Boolean> toFrontListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				if (arg2 != null && arg2) {
					pane.toFront();
					pane.setStyle("-fx-background-color: #eaeaea");
				}
				else {
					pane.setStyle("-fx-background-color: #ffffff");
				}
			}
		};
		pane.focusedProperty().addListener(toFrontListener);
		
		pane.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				pane.requestFocus();
				SimpleProperty<Integer> invocationProperty = new SimpleProperty<Integer>("invocationOrder", Integer.class, true);
				EnumeratedSimpleProperty<String> targetProperty = new EnumeratedSimpleProperty<String>("target", String.class, false);
				SimpleProperty<Boolean> asynchronousProperty = new SimpleProperty<Boolean>("asynchronous", Boolean.class, false);
				targetProperty.addAll(InvokeWrapper.this.service.getExecutorProvider().getTargets().toArray(new String[0]));
//				targetProperty.addAll("$self", "$any", "$all", "$other");
				HashSet<Property<?>> hashSet = new HashSet<Property<?>>(Arrays.asList(invocationProperty, targetProperty, asynchronousProperty));
				
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
		VBox vbox = new VBox();
		vbox.getStyleClass().add("invokeWrapper");
		HBox name = new HBox();
		name.getStyleClass().add("invokeName");
		Label nameLabel = new Label(invoke.getServiceId());
		nameLabel.getStyleClass().add("invokeServiceName");
		Label invokeLevelLabel = new Label("[" + invoke.getInvocationOrder() + "]");
		invokeLevelLabel.getStyleClass().add("invokeOrder");
		name.getChildren().addAll(invokeLevelLabel, nameLabel);
		vbox.getChildren().add(name);
		
		vbox.getStyleClass().add("service");
		if (service != null) {
			input = new Tree<Element<?>>(new ElementMarshallable());
			EAIDeveloperUtils.addElementExpansionHandler(input);
			input.setClipboardHandler(new ElementClipboardHandler(input, false));
			input.set("invoke", invoke);
			input.rootProperty().set(new ElementTreeItem(new RootElement(service.getServiceInterface().getInputDefinition(), "input"), null, false, false));
			input.getTreeCell(input.rootProperty().get()).expandedProperty().set(false);
			TreeDragDrop.makeDroppable(input, new DropLinkListener(controller, mappings, this.service, serviceController, serviceTree, lock, repository, sourceId));
			input.getRootCell().getNode().getStyleClass().add("invokeTree");
			
			output = new Tree<Element<?>>(new ElementMarshallable());
			EAIDeveloperUtils.addElementExpansionHandler(output);
			output.setClipboardHandler(new ElementClipboardHandler(output, false));
			
			updateOutput(service);
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
		pane.setManaged(false);
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
