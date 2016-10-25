package be.nabu.eai.module.services.vm;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ArtifactGUIInstance;
import be.nabu.eai.developer.api.ConfigurableGUIManager;
import be.nabu.eai.developer.api.InterfaceLister;
import be.nabu.eai.developer.api.InterfaceLister.InterfaceDescription;
import be.nabu.eai.developer.api.PortableArtifactGUIManager;
import be.nabu.eai.developer.api.ValidationSelectableArtifactGUIManager;
import be.nabu.eai.developer.components.RepositoryBrowser;
import be.nabu.eai.developer.controllers.VMServiceController;
import be.nabu.eai.developer.managers.ServiceGUIManager;
import be.nabu.eai.developer.managers.util.DoubleAmountListener;
import be.nabu.eai.developer.managers.util.ElementLineConnectListener;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.managers.util.MovablePane;
import be.nabu.eai.developer.managers.util.RootElementWithPush;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.ElementClipboardHandler;
import be.nabu.eai.developer.util.ElementSelectionListener;
import be.nabu.eai.developer.util.ElementTreeItem;
import be.nabu.eai.module.services.vm.util.DropLinkListener;
import be.nabu.eai.module.services.vm.util.DropWrapper;
import be.nabu.eai.module.services.vm.util.FixedValue;
import be.nabu.eai.module.services.vm.util.InvokeWrapper;
import be.nabu.eai.module.services.vm.util.LinkPropertyUpdater;
import be.nabu.eai.module.services.vm.util.Mapping;
import be.nabu.eai.module.services.vm.util.RemoveLinkListener;
import be.nabu.eai.module.services.vm.util.StepClipboardHandler;
import be.nabu.eai.module.services.vm.util.StepPropertyProvider;
import be.nabu.eai.module.services.vm.util.StepTreeItem;
import be.nabu.eai.module.types.structure.StructureGUIManager;
import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.tree.Marshallable;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.jfx.control.tree.drag.TreeDragDrop;
import be.nabu.jfx.control.tree.drag.TreeDragListener;
import be.nabu.jfx.control.tree.drag.TreeDropListener;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.SimpleExecutionContext;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.Break;
import be.nabu.libs.services.vm.step.Catch;
import be.nabu.libs.services.vm.step.Drop;
import be.nabu.libs.services.vm.step.Finally;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.LimitedStepGroup;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.services.vm.step.Switch;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.ValidateProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class VMServiceGUIManager implements PortableArtifactGUIManager<VMService>, ConfigurableGUIManager<VMService>, ValidationSelectableArtifactGUIManager<VMService> {

	static {
		URL resource = VMServiceGUIManager.class.getClassLoader().getResource("vmservice.css");
		if (resource != null) {
			MainController.registerStyleSheet(resource.toExternalForm());
		}
	}
	
	private boolean disablePipelineEditing;
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public static final String DATA_TYPE_STEP = "vmservice-step";
	private boolean removeInvalid = true;
	
	public static final String INTERFACE_EDITABLE = "interfaceEditable";
	private java.util.Map<String, String> configuration;
	
	@Override
	public ArtifactManager<VMService> getArtifactManager() {
		return new VMServiceManager();
	}
	
	private java.util.Map<Class<? extends Step>, Button> addButtons = new HashMap<Class<? extends Step>, Button>();
	private java.util.Map<Link, Mapping> mappings = new LinkedHashMap<Link, Mapping>();
	private java.util.Map<Link, FixedValue> fixedValues = new LinkedHashMap<Link, FixedValue>();
	private Tree<Element<?>> inputTree;
	private Tree<Element<?>> outputTree;
	private Tree<Element<?>> leftTree;
	private Tree<Element<?>> rightTree;
	private java.util.Map<String, InvokeWrapper> invokeWrappers;
	private java.util.Map<Drop, DropWrapper> drops = new HashMap<Drop, DropWrapper>();

	private Tree<Step> serviceTree;

	@Override
	public String getArtifactName() {
		return "Flow Service";
	}

	@Override
	public ImageView getGraphic() {
		return MainController.loadGraphic("vmservice.png");
	}

	protected List<Property<?>> getCreateProperties() {
		return null;
	}
	
	protected VMService newVMService(Repository repository, String id, Value<?>...values) {
		SimpleVMServiceDefinition service = new SimpleVMServiceDefinition(new Pipeline(new Structure(), new Structure()));
		service.setId(id);
		return service;
	}
	
	@Override
	public ArtifactGUIInstance create(final MainController controller, final TreeItem<Entry> target) throws IOException {
		List<Property<?>> properties = new ArrayList<Property<?>>();
		properties.add(new SimpleProperty<String>("Name", String.class, true));
		List<Property<?>> createProperties = getCreateProperties();
		if (createProperties != null) {
			properties.addAll(createProperties);
		}
		final SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new LinkedHashSet<Property<?>>(properties));
		final VMServiceGUIInstance instance = new VMServiceGUIInstance(this);
		EAIDeveloperUtils.buildPopup(controller, updater, "Create " + getArtifactName(), new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				try {
					String name = updater.getValue("Name");
					RepositoryEntry entry = ((RepositoryEntry) target.itemProperty().get()).createNode(name, getArtifactManager(), true);
					VMService service = newVMService(entry.getRepository(), entry.getId(), updater.getValues());
					getArtifactManager().save(entry, service);
					controller.getRepositoryBrowser().refresh();
					Tab tab = controller.newTab(entry.getId(), instance);
					AnchorPane pane = new AnchorPane();
					tab.setContent(pane);
					ServiceGUIManager.makeRunnable(tab, service, controller);
					display(controller, pane, service);
					instance.setEntry(entry);
					instance.setService(service);
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
				catch (ParseException e) {
					throw new RuntimeException(e);
				}
			}
		});
		return instance;
	}
	
	@Override
	public ArtifactGUIInstance view(MainController controller, TreeItem<Entry> target) throws IOException, ParseException {
		VMServiceGUIInstance instance = new VMServiceGUIInstance(this, target.itemProperty().get(), null);
		Tab tab = controller.newTab(target.itemProperty().get().getId(), instance);
		AnchorPane pane = new AnchorPane();
		tab.setContent(pane);
		ServiceGUIManager.makeRunnable(tab, (VMService) target.itemProperty().get().getNode().getArtifact(), controller);
		instance.setService(display(controller, pane, target.itemProperty().get()));
		return instance;
	}
	
	public static TreeItem<Element<?>> find(TreeItem<Element<?>> parent, ParsedPath path) {
		for (TreeItem<Element<?>> child : parent.getChildren()) {
			if (child.getName().equals(path.getName())) {
				return path.getChildPath() == null ? child : find(child, path.getChildPath());
			}
		}
		return null;
	}
	
	VMService display(final MainController controller, Pane pane, Entry entry) throws IOException, ParseException {
		VMService service = (VMService) entry.getNode().getArtifact();
		displayWithController(controller, pane, service);
		return service;
	}
	
	public VMServiceController displayWithController(final MainController controller, Pane pane, final VMService service) throws IOException, ParseException {
		FXMLLoader loader = controller.load("vmservice.fxml", "Service", false);
		final VMServiceController serviceController = loader.getController();
		
		// the top part is the service, the bottom is a tabpane with input/output & mapping
		SplitPane splitPane = new SplitPane();
		
		AnchorPane top = new AnchorPane();
		splitPane.getItems().add(top);
		serviceTree = new Tree<Step>(new StepMarshallable());
		serviceTree.rootProperty().set(new StepTreeItem(service.getRoot(), null, false));
		serviceTree.getRootCell().expandedProperty().set(true);
		// disable map tab
		serviceController.getTabMap().setDisable(true);
		
		serviceTree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Step>>() {
			@Override
			public void changed(ObservableValue<? extends TreeCell<Step>> arg0, TreeCell<Step> arg1, TreeCell<Step> arg2) {
				if (arg2 != null) {
					controller.showProperties(new StepPropertyProvider(arg2));
				}
			}
		});
		serviceTree.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getCode() == KeyCode.F1) {
					TreeCell<Step> selectedItem = serviceTree.getSelectionModel().getSelectedItem();
					// can never disable root
					if (selectedItem != null && selectedItem.getParent() != null) {
						Boolean current = ((StepTreeItem) selectedItem.getItem()).disableProperty().get();
						((StepTreeItem) selectedItem.getItem()).disableProperty().set(!current);
						MainController.getInstance().setChanged();
					}
				}
				else if (event.getCode() == KeyCode.E && event.isControlDown()) {
					TreeCell<Step> selectedItem = serviceTree.getSelectionModel().getSelectedItem();
					selectedItem.expandAll();
				}
			}
		});

		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Sequence.class));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Map.class));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, For.class));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Switch.class));		
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Catch.class));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Finally.class));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Throw.class));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Break.class));
		
		TextField search = new TextField();
		search.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				highlight(arg2);
			}
		});
		serviceController.getHbxButtons().getChildren().add(search);

		serviceController.getPanService().getChildren().add(serviceTree);
		
		serviceTree.prefWidthProperty().bind(serviceController.getPanService().widthProperty());

		Parent parent = loader.getRoot();
		pane.getChildren().add(parent);
		// make sure it is full size
		AnchorPane.setTopAnchor(parent, 0d);
		AnchorPane.setBottomAnchor(parent, 0d);
		AnchorPane.setLeftAnchor(parent, 0d);
		AnchorPane.setRightAnchor(parent, 0d);

		TreeDragDrop.makeDraggable(serviceTree, new TreeDragListener<Step>() {
			@Override
			public boolean canDrag(TreeCell<Step> arg0) {
				return arg0.getItem().getParent() != null;
			}
			@Override
			public void drag(TreeCell<Step> arg0) {
				// do nothing
			}
			@Override
			public String getDataType(TreeCell<Step> arg0) {
				return DATA_TYPE_STEP;
			}
			@Override
			public TransferMode getTransferMode() {
				return TransferMode.MOVE;
			}
			@Override
			public void stopDrag(TreeCell<Step> arg0, boolean arg1) {
				// do nothing
			}
		});
		TreeDragDrop.makeDroppable(serviceTree, new TreeDropListener<Step>() {
			@Override
			public boolean canDrop(String dataType, TreeCell<Step> target, TreeCell<?> dragged, TransferMode transferMode) {
				if (!dataType.equals(DATA_TYPE_STEP)) {
					return false;
				}
				else if (target.getItem().itemProperty().get() instanceof StepGroup) {
					// not to itself
					if (target.getItem().equals(dragged.getParent())) {
						return false;
					}
					// if it's a limited group, check the type
					if (target.getItem().itemProperty().get() instanceof LimitedStepGroup) {
						return ((LimitedStepGroup) target.getItem().itemProperty().get()).getAllowedSteps().contains(dragged.getItem().itemProperty().get().getClass());
					}
					else {
						return true;
					}
				}
				return false;
			}
			@SuppressWarnings("unchecked")
			@Override
			public void drop(String dataType, TreeCell<Step> target, TreeCell<?> dragged, TransferMode transferMode) {
				StepGroup newParent = (StepGroup) target.getItem().itemProperty().get();
				TreeCell<Step> draggedElement = (TreeCell<Step>) dragged;
				StepGroup originalParent = (StepGroup) draggedElement.getItem().getParent().itemProperty().get();
				if (originalParent.getChildren().remove(draggedElement.getItem().itemProperty().get())) {
					newParent.getChildren().add(draggedElement.getItem().itemProperty().get());
					draggedElement.getItem().itemProperty().get().setParent(newParent);
				}
				// refresh both
				((StepTreeItem) target.getItem()).refresh();
				((StepTreeItem) dragged.getParent().getItem()).refresh();
				MainController.getInstance().setChanged();
			}
		});
		
		// show the input & output
		StructureGUIManager structureManager = new StructureGUIManager();
		VBox input = new VBox();
		RootElementWithPush element = new RootElementWithPush(
			(Structure) service.getPipeline().get(Pipeline.INPUT).getType(), 
			false,
			service.getPipeline().get(Pipeline.INPUT).getProperties()
		);
		// block all properties for the input field
		element.getBlockedProperties().addAll(element.getSupportedProperties());
		
		inputTree = structureManager.display(controller, input, element, isInterfaceEditable(), false);
		inputTree.setClipboardHandler(new ElementClipboardHandler(inputTree));
		serviceController.getPanInput().getChildren().add(input);
		
		AnchorPane.setTopAnchor(input, 0d);
		AnchorPane.setBottomAnchor(input, 0d);
		AnchorPane.setLeftAnchor(input, 0d);
		AnchorPane.setRightAnchor(input, 0d);
		
		VBox output = new VBox();
		element = new RootElementWithPush(
			(Structure) service.getPipeline().get(Pipeline.OUTPUT).getType(), 
			false,
			service.getPipeline().get(Pipeline.OUTPUT).getProperties()
		);
		// block all properties for the output field
		element.getBlockedProperties().addAll(element.getSupportedProperties());
		
		outputTree = structureManager.display(controller, output, element, isInterfaceEditable(), false);
		outputTree.setClipboardHandler(new ElementClipboardHandler(outputTree));
		serviceController.getPanOutput().getChildren().add(output);

		AnchorPane.setTopAnchor(output, 0d);
		AnchorPane.setBottomAnchor(output, 0d);
		AnchorPane.setLeftAnchor(output, 0d);
		AnchorPane.setRightAnchor(output, 0d);
		
		// populate the interfaces
		serviceController.getMnuInterfaces().getItems().clear();
		java.util.Map<String, Menu> map = new TreeMap<String, Menu>();
		for (InterfaceLister lister : ServiceLoader.load(InterfaceLister.class)) {
			for (InterfaceDescription description : lister.getInterfaces()) {
				if (!map.containsKey(description.getCategory())) {
					map.put(description.getCategory(), new Menu(description.getCategory()));
				}
				MenuItem item = new MenuItem(description.getName());
				item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						serviceController.getTxtInterface().setText(description.getInterface());
					}
				});
				map.get(description.getCategory()).getItems().add(item);
			}
		}
		for (String category : map.keySet()) {
			serviceController.getMnuInterfaces().getItems().add(map.get(category));
		}
		
		DefinedServiceInterface value = ValueUtils.getValue(PipelineInterfaceProperty.getInstance(), service.getPipeline().getProperties());
		serviceController.getTxtInterface().setText(value == null ? null : value.getId());
		serviceController.getTxtInterface().setDisable(!isInterfaceEditable());
		// show the service interface
		serviceController.getTxtInterface().textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				if (arg2 == null || arg2.isEmpty()) {
					// unset the pipeline attribute
					service.getPipeline().setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), null));
					// reload
					inputTree.refresh();
					outputTree.refresh();
					MainController.getInstance().setChanged();
				}
				else {
					DefinedServiceInterface iface = DefinedServiceInterfaceResolverFactory.getInstance().getResolver().resolve(arg2);
					if (iface != null) {
						// reset the pipeline attribute
						service.getPipeline().setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), iface));
						// reload
						inputTree.refresh();
						outputTree.refresh();
						MainController.getInstance().setChanged();
					}
					else {
						controller.notify(new ValidationMessage(Severity.ERROR, "The indicated node is not a service interface: " + arg2));
					}
				}
			}
		});
		
		// the input/output validation
		serviceController.getChkValidateInput().setSelected(ValueUtils.getValue(ValidateProperty.getInstance(), service.getPipeline().get(Pipeline.INPUT).getProperties()));
		serviceController.getChkValidateOutput().setSelected(ValueUtils.getValue(ValidateProperty.getInstance(), service.getPipeline().get(Pipeline.OUTPUT).getProperties()));
		
		// @2016-01-15: fun fact: i added container types which can contain for example services
		// now the container upon save() will redraw the container (to ensure if artifacts impact one another they show the latest version)
		// upon redraw (without reload) the rootelementwithpush was created around the complex type with the validate set true on the element
		// the rootelementwithpush pushes this to the type and it could never be turned off
		// unsetting it would remove the setting (it has a default value!) so it would not be removed from the type!
		// anyway, it doesn't occur in non-container artifacts, presumably because we don't "reload" the GUI hence never pushing the property to the type
		// currently the workaround is to specifically ignore validateproperty in rootelementwithpush, this is not ideal but it'll work
		// note that these are all front-end problems, they don't impact runtime which is why they don't require (in the absolute sense) a clean solution
		serviceController.getChkValidateInput().selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				service.getPipeline().get(Pipeline.INPUT).setProperty(new ValueImpl<Boolean>(ValidateProperty.getInstance(), arg2));
				controller.setChanged();
			}
		});
		serviceController.getChkValidateOutput().selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				service.getPipeline().get(Pipeline.OUTPUT).setProperty(new ValueImpl<Boolean>(ValidateProperty.getInstance(), arg2));
				controller.setChanged();
			}
		});
		
		leftTree = buildLeftPipeline(controller, serviceController, service.getRoot());
		rightTree = buildRightPipeline(controller, service, serviceTree, serviceController, service.getRoot());
		
		serviceTree.setClipboardHandler(new StepClipboardHandler(serviceTree));
		// if we select a map step, we have to show the mapping screen
		serviceTree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Step>>() {
			@Override
			public void changed(ObservableValue<? extends TreeCell<Step>> arg0, TreeCell<Step> arg1, TreeCell<Step> arg2) {
				Step step = arg2.getItem().itemProperty().get();
				
				// refresh the step
				arg2.getItem().itemProperty().get().refresh();
				
				// enable/disable buttons depending on the selection
				// first just disable all buttons
				for (Button button : addButtons.values()) {
					button.disableProperty().set(true);
				}
				// for a stepgroup, reenable some or all buttons
				if (step instanceof StepGroup) {
					for (Class<? extends Step> supported : step instanceof LimitedStepGroup ? ((LimitedStepGroup) step).getAllowedSteps() : addButtons.keySet()) {
						if (addButtons.containsKey(supported)) {
							addButtons.get(supported).disableProperty().set(false);
						}
					}
				}
				
				// if the new selection is not a map, or not the same map, clear it
				if (!(step instanceof Map) || !arg2.equals(arg1)) {
					serviceController.getTabMap().setDisable(true);
					// remove all the current lines
					for (Mapping mapping : mappings.values()) {
						mapping.remove();
					}
					mappings.clear();
					// remove all the set values
					for (FixedValue fixedValue : fixedValues.values()) {
						fixedValue.remove();
					}
					fixedValues.clear();
					// clear left & right & center
					serviceController.getPanLeft().getChildren().clear();
					serviceController.getPanRight().getChildren().clear();
					serviceController.getPanMiddle().getChildren().clear();
				}
				// if the new selection is a map, draw everything
				if (arg2.getItem().itemProperty().get() instanceof Map) {
					leftTree = buildLeftPipeline(controller, serviceController, (Map) arg2.getItem().itemProperty().get());
					rightTree = buildRightPipeline(controller, service, serviceTree, serviceController, (Map) arg2.getItem().itemProperty().get());
					
					serviceController.getTabMap().setDisable(false);
					
					// first draw all the invokes and build a map of temporary result mappings
					invokeWrappers = new HashMap<String, InvokeWrapper>();
					for (final Step child : ((Map) arg2.getItem().itemProperty().get()).getChildren()) {
						if (child instanceof Invoke) {
							drawInvoke(controller, (Invoke) child, invokeWrappers, serviceController, service, serviceTree);
						}
					}
					Iterator<Step> iterator = ((Map) arg2.getItem().itemProperty().get()).getChildren().iterator();
					// loop over the invoke again but this time to draw links
					while (iterator.hasNext()) {
						Step child = iterator.next();
						if (child instanceof Invoke) {
							Iterator<Step> linkIterator = ((Invoke) child).getChildren().iterator();
							while(linkIterator.hasNext()) {
								Step linkChild = linkIterator.next();
								final Link link = (Link) linkChild;
								if (link.isFixedValue()) {
									// must be mapped to the input of an invoke
									Tree<Element<?>> tree = invokeWrappers.get(((Invoke) child).getResultName()).getInput();
									FixedValue fixedValue = buildFixedValue(controller, tree, link);
									if (fixedValue == null) {
										controller.notify(new ValidationMessage(Severity.ERROR, "The fixed value to " + link.getTo() + " is no longer valid"));
										if (removeInvalid) {
											linkIterator.remove();
										}
									}
									else {
										fixedValues.put(link, fixedValue);
									}
								}
								else {
									// a link in an invoke always maps to that invoke, so substitute the right tree for the input of this invoke
									Mapping mapping = buildMapping(
										link, 
										serviceController.getPanMap(), 
										leftTree, 
										invokeWrappers.get(((Invoke) child).getResultName()).getInput(), 
										invokeWrappers
									);
									if (mapping == null) {
										controller.notify(new ValidationMessage(Severity.ERROR, "The mapping from " + ((Link) link).getFrom() + " to " + ((Link) link).getTo() + " is no longer valid, it will be removed"));
										linkIterator.remove();
										MainController.getInstance().setChanged();
									}
									else {
										mappings.put(link, mapping);
										mapping.getShape().addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
											@Override
											public void handle(MouseEvent arg0) {
												controller.showProperties(new LinkPropertyUpdater(link, mapping));
											}
										});
									}
								}
							}
						}
					}
					// reinitialize all the drops
					drops.clear();
					// draw all the links from the mappings
					iterator = ((Map) arg2.getItem().itemProperty().get()).getChildren().iterator();
					while (iterator.hasNext()) {
						Step child = iterator.next();
						if (child instanceof Drop) {
							DropWrapper wrapper = buildDropWrapper(rightTree, (Drop) child);
							if (wrapper == null) {
								controller.notify(new ValidationMessage(Severity.ERROR, "The drop " + ((Drop) child).getPath() + " is no longer valid"));
								if (removeInvalid) {
									iterator.remove();
								}
							}
							else {
								drops.put((Drop) child, wrapper);
							}
						}
						else if (child instanceof Link) {
							final Link link = (Link) child;
							if (link.isFixedValue()) {
								FixedValue fixedValue = buildFixedValue(controller, rightTree, link);
								if (fixedValue == null) {
									controller.notify(new ValidationMessage(Severity.ERROR, "The fixed value to " + link.getTo() + " is no longer valid"));
									if (removeInvalid) {
										iterator.remove();
									}
								}
								else {
									fixedValues.put(link, fixedValue);
								}
							}
							else {
								Mapping mapping = buildMapping(link, serviceController.getPanMap(), leftTree, rightTree, invokeWrappers);
								// don't remove the mapping alltogether, the user might want to fix it or investigate it
								if (mapping == null) {
									controller.notify(new ValidationMessage(Severity.ERROR, "The mapping from " + link.getFrom() + " to " + link.getTo() + " is no longer valid"));
									if (removeInvalid) {
										iterator.remove();
									}
								}
								else {
									mappings.put(link, mapping);
									mapping.getShape().addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
										@Override
										public void handle(MouseEvent arg0) {
											controller.showProperties(new LinkPropertyUpdater(link, mapping));
										}
									});
								}
							}
						}
					}
					List<ValidationMessage> calculateInvocationOrder = ((Map) arg2.getItem().itemProperty().get()).calculateInvocationOrder();
					if (!calculateInvocationOrder.isEmpty()) {
						controller.notify(calculateInvocationOrder);
					}
				}
			}

		});
		
		// the service controller resizes the scroll pane based on this pane
		// so bind it to the the tree
		
//		serviceController.getPanLeft().prefWidthProperty().bind(leftTree.widthProperty());
//		serviceController.getPanRight().prefWidthProperty().bind(rightTree.widthProperty());
		
		serviceController.getPanMiddle().addEventHandler(DragEvent.DRAG_OVER, new EventHandler<DragEvent>() {
			@Override
			public void handle(DragEvent event) {
				if (serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get() instanceof Map) {
					Dragboard dragboard = event.getDragboard();
					if (dragboard != null) {
						Object content = dragboard.getContent(TreeDragDrop.getDataFormat(RepositoryBrowser.getDataType(DefinedService.class)));
						// this will be the path in the tree
						if (content != null) {
							String serviceId = controller.getRepositoryBrowser().getControl().resolve((String) content).itemProperty().get().getId();
							if (serviceId != null) {
								event.acceptTransferModes(TransferMode.MOVE);
								event.consume();
							}
						}
					}
				}
			}
		});
		serviceController.getPanMiddle().addEventHandler(DragEvent.DRAG_DROPPED, new EventHandler<DragEvent>() {
			@Override
			public void handle(DragEvent event) {
				if (serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get() instanceof Map) {
					Map target = (Map) serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get();
					Dragboard dragboard = event.getDragboard();
					if (dragboard != null) {
						Object content = dragboard.getContent(TreeDragDrop.getDataFormat(RepositoryBrowser.getDataType(DefinedService.class)));
						// this will be the path in the tree
						if (content != null) {
							String serviceId = controller.getRepositoryBrowser().getControl().resolve((String) content).itemProperty().get().getId();
							if (serviceId != null) {
								Invoke invoke = new Invoke();
								invoke.setParent(target);
								invoke.setServiceId(serviceId);
								// the position is relative to where you dropped it, not the scene!
//									invoke.setX(event.getSceneX());
//									invoke.setY(event.getSceneY());
								invoke.setX(event.getX());
								invoke.setY(event.getY());
								target.getChildren().add(invoke);
								drawInvoke(controller, invoke, invokeWrappers, serviceController, service, serviceTree);
								serviceTree.getSelectionModel().getSelectedItem().refresh();
								MainController.getInstance().setChanged();
							}
						}
					}
				}
			}
		});
		return serviceController;
	}
	
	public void highlight(String text) {
		highlight(serviceTree.rootProperty().get(), text);
	}
	
	private void highlight(TreeItem<Step> item, String text) {
		TreeCell<Step> cell = serviceTree.getTreeCell(item);
		cell.getCellValue().getNode().getStyleClass().remove("highlightedStep");
		if (text != null && !text.trim().isEmpty() && matches(item.itemProperty().get(), text, false)) {
			cell.getCellValue().getNode().getStyleClass().add("highlightedStep");
		}
		if (item.getChildren() != null && !item.getChildren().isEmpty()) {
			for (TreeItem<Step> child : item.getChildren()) {
				highlight(child, text);
			}
		}
		// children but none of them are shown in the tree
		else if (text != null && !text.trim().isEmpty() && item.itemProperty().get() instanceof StepGroup && matches(item.itemProperty().get(), text, true)) {
			cell.getCellValue().getNode().getStyleClass().add("highlightedStep");
		}
	}
	
	private static boolean matches(Step step, String text, boolean recursive) {
		String regex = "(?i).*" + text + ".*";
		if (step.getId() != null && step.getId().equals(text)) {
			return true;
		}
		else if (step.getComment() != null && step.getComment().matches(regex)) {
			return true;
		}
		else if (step instanceof Link) {
			if (((Link) step).getFrom().matches(regex)) {
				return true;
			}
			else if (((Link) step).getTo().matches(regex)) {
				return true;
			}
		}
		else if (step instanceof Invoke) {
			if (((Invoke) step).getServiceId().matches(regex)) {
				return true;
			}
		}
		if (recursive && step instanceof StepGroup) {
			for (Step child : ((StepGroup) step).getChildren()) {
				if (matches(child, text, recursive)) {
					return true;
				}
			}
		}
		return false;
	}
	
	private InvokeWrapper drawInvoke(MainController controller, final Invoke invoke, java.util.Map<String, InvokeWrapper> invokeWrappers, VMServiceController serviceController, VMService service, Tree<Step> serviceTree) {
		InvokeWrapper invokeWrapper = new InvokeWrapper(controller, invoke, serviceController.getPanMiddle(), service, serviceController, serviceTree, mappings);
		invokeWrappers.put(invoke.getResultName(), invokeWrapper);
		Pane pane = invokeWrapper.getComponent();
		serviceController.getPanMiddle().getChildren().add(pane);
		MovablePane movable = MovablePane.makeMovable(pane);
		movable.setGridSize(10);
		movable.xProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				invoke.setX(arg2.doubleValue());
				MainController.getInstance().setChanged();
			}
		});
		movable.yProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				invoke.setY(arg2.doubleValue());
				MainController.getInstance().setChanged();
			}
		});
		FixedValue.allowFixedValue(controller, fixedValues, serviceTree, invokeWrapper.getInput());
		
		resizeIfTooBig(invokeWrapper, pane, serviceController);
		return invokeWrapper;
	}
	
	private static void resizeIfTooBig(InvokeWrapper wrapper, Pane pane, VMServiceController controller) {
		if (wrapper.getInput() != null && wrapper.getOutput() != null) {
			DoubleAmountListener heightListener = new DoubleAmountListener(wrapper.getInput().heightProperty(), wrapper.getOutput().heightProperty());
			DoubleAmountListener widthListener = new DoubleAmountListener(wrapper.getInput().widthProperty(), wrapper.getOutput().widthProperty());
			pane.layoutYProperty().add(heightListener.maxDoubleProperty()).addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
					if (arg2.doubleValue() > controller.getPanMiddle().getHeight()) {
						controller.getPanMiddle().setPrefHeight(arg2.doubleValue());
						controller.getPanMiddle().setMinHeight(arg2.doubleValue());
					}
				}
			});
			pane.layoutXProperty().add(widthListener.maxDoubleProperty()).addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
					if (arg2.doubleValue() > controller.getPanMiddle().getWidth()) {
						controller.getPanMiddle().setPrefWidth(arg2.doubleValue());
						controller.getPanMiddle().setMinWidth(arg2.doubleValue());
					}
				}
			});
		}
	}
	
	private Button createAddButton(Tree<Step> serviceTree, Class<? extends Step> clazz) {
		Button button = new Button();
		button.setTooltip(new Tooltip(clazz.getSimpleName()));
		button.setGraphic(MainController.loadGraphic(getIcon(clazz)));
		button.addEventHandler(ActionEvent.ACTION, new ServiceAddHandler(serviceTree, clazz));
		addButtons.put(clazz, button);
		return button;
	}
	
	public class DropButton extends Button {
		
		private Tree<Element<?>> tree;
		
		public DropButton(final StepGroup step) {
			setTooltip(new Tooltip("Drop"));
			setGraphic(MainController.loadGraphic("drop.png"));
			addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event) {
					if (tree != null) {
						TreeCell<Element<?>> selected = tree.getSelectionModel().getSelectedItem();
						if (selected != null) {
							String path = TreeDragDrop.getPath(selected.getItem());
							if (path.startsWith("pipeline/")) {
								path = path.substring("pipeline/".length());
								Iterator<Step> iterator = step.getChildren().iterator();
								boolean existing = false;
								// if the drop already exists, undrop it
								while(iterator.hasNext()) {
									Step child = iterator.next();
									if (child instanceof Drop && path.equals(((Drop) child).getPath())) {
										existing = true;
										iterator.remove();
										if (drops.containsKey(child)) {
											drops.get(child).remove();
											drops.remove(child);
										}
										MainController.getInstance().setChanged();
										break;
									}
								}
								// otherwise add a drop
								if (!existing) {
									Drop drop = new Drop();
									drop.setPath(path);
									drop.setParent(step);
									step.getChildren().add(drop);
									drops.put(drop, new DropWrapper(selected, drop));
									MainController.getInstance().setChanged();
								}
							}
						}
					}
				}
			});
		}

		public Tree<Element<?>> getTree() {
			return tree;
		}

		public void setTree(Tree<Element<?>> tree) {
			this.tree = tree;
		}
	}
	
	private Tree<Element<?>> buildRightPipeline(MainController controller, VMService service, Tree<Step> serviceTree, VMServiceController serviceController, StepGroup step) {
		// remove listeners
		if (rightTree != null) {
			inputTree.removeRefreshListener(rightTree.getTreeCell(rightTree.rootProperty().get()));
			outputTree.removeRefreshListener(rightTree.getTreeCell(rightTree.rootProperty().get()));
		}
		final VBox right = new VBox();
		
		StructureGUIManager structureManager = new StructureGUIManager();
		try {
			// drop button was added afterwards, hence the mess
			DropButton dropButton = new DropButton(step);
			Tree<Element<?>> rightTree = structureManager.display(controller, right, new RootElementWithPush(
				(Structure) step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()), 
				false,
				step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()).getProperties()
			), !disablePipelineEditing, true, dropButton);
			dropButton.setTree(rightTree);
		
			// make sure the "input" & "output" are not editable
			for (TreeItem<Element<?>> item : rightTree.rootProperty().get().getChildren()) {
				if (item.itemProperty().get().getName().equals("input") || item.itemProperty().get().getName().equals("output")) {
					item.editableProperty().set(false);
				}
				else {
					item.editableProperty().set(true);
				}
			}
			serviceController.getPanRight().getChildren().add(right);
			
			AnchorPane.setLeftAnchor(right, 0d);
			AnchorPane.setRightAnchor(right, 0d);
			AnchorPane.setTopAnchor(right, 0d);
			AnchorPane.setBottomAnchor(right, 0d);
			
			// make sure the left & right trees are refreshed if the input/output is updated
			inputTree.addRefreshListener(rightTree.getTreeCell(rightTree.rootProperty().get()));
			outputTree.addRefreshListener(rightTree.getTreeCell(rightTree.rootProperty().get()));
			
			TreeDragDrop.makeDroppable(rightTree, new DropLinkListener(controller, mappings, service, serviceController, serviceTree));
			FixedValue.allowFixedValue(controller, fixedValues, serviceTree, rightTree);
			
			rightTree.setClipboardHandler(new ElementClipboardHandler(rightTree));
			return rightTree;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	private Tree<Element<?>> buildLeftPipeline(MainController controller, VMServiceController serviceController, StepGroup step) {
		if (leftTree != null) {
			inputTree.removeRefreshListener(leftTree.getTreeCell(leftTree.rootProperty().get()));
			outputTree.removeRefreshListener(leftTree.getTreeCell(leftTree.rootProperty().get()));
		}
		Tree<Element<?>> leftTree = new Tree<Element<?>>(new ElementMarshallable());
		leftTree.rootProperty().set(new ElementTreeItem(new RootElementWithPush(
			(Structure) step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()),
			false,
			step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()).getProperties()
		), null, false, false));
		// show properties if selected
		leftTree.getSelectionModel().selectedItemProperty().addListener(new ElementSelectionListener(controller, false));
		// add first to get parents right
		serviceController.getPanLeft().getChildren().add(leftTree);
		
		AnchorPane.setLeftAnchor(leftTree, 0d);
		AnchorPane.setRightAnchor(leftTree, 0d);
		AnchorPane.setTopAnchor(leftTree, 0d);
		AnchorPane.setBottomAnchor(leftTree, 0d);
		
		TreeDragDrop.makeDraggable(leftTree, new ElementLineConnectListener(serviceController.getPanMap()));
		inputTree.addRefreshListener(leftTree.getTreeCell(leftTree.rootProperty().get()));
		outputTree.addRefreshListener(leftTree.getTreeCell(leftTree.rootProperty().get()));
		
		leftTree.setClipboardHandler(new ElementClipboardHandler(leftTree, false));
		return leftTree;
	}
	
	private DropWrapper buildDropWrapper(Tree<Element<?>> tree, Drop drop) {
		TreeItem<Element<?>> target = VMServiceGUIManager.find(tree.rootProperty().get(), new ParsedPath(drop.getPath()));
		if (target == null) {
			return null;
		}
		return new DropWrapper(tree.getTreeCell(target), drop);
	}
	
	private FixedValue buildFixedValue(MainController controller, Tree<Element<?>> tree, Link link) {
		TreeItem<Element<?>> target = VMServiceGUIManager.find(tree.rootProperty().get(), new ParsedPath(link.getTo()));
		if (target == null) {
			return null;
		}
		return new FixedValue(controller, tree.getTreeCell(target), link);
	}
	
	private Mapping buildMapping(Link link, Pane target, Tree<Element<?>> left, Tree<Element<?>> right, java.util.Map<String, InvokeWrapper> invokeWrappers) {
		ParsedPath from = new ParsedPath(link.getFrom());
		TreeItem<Element<?>> fromElement;
		Tree<Element<?>> fromTree;
		// this means you are mapping it from another invoke, use that output tree to find the element
		if (invokeWrappers.containsKey(from.getName())) {
			fromElement = find(invokeWrappers.get(from.getName()).getOutput().rootProperty().get(), from.getChildPath());
			fromTree = invokeWrappers.get(from.getName()).getOutput();
		}
		// otherwise, it's from the pipeline
		else {
			fromElement = find(left.rootProperty().get(), from);
			fromTree = left;
		}
		ParsedPath to = new ParsedPath(link.getTo());
		TreeItem<Element<?>> toElement;
		Tree<Element<?>> toTree;
		if (invokeWrappers.containsKey(to.getName())) {
			toElement = find(invokeWrappers.get(to.getName()).getInput().rootProperty().get(), to);
			toTree = invokeWrappers.get(to.getName()).getInput();
		}
		// otherwise, it's from the pipeline
		else {
			toElement = find(right.rootProperty().get(), to);
			toTree = right;
		}
		
		if (fromElement == null || toElement == null) {
			logger.error("Can not create link from " + from + " (" + fromElement + ") to " + to + " (" + toElement + ")");
			return null;
		}
		else {
			Mapping mapping = new Mapping(target, fromTree.getTreeCell(fromElement), toTree.getTreeCell(toElement));
			mapping.setRemoveMapping(new RemoveLinkListener(link));
			if (link.isMask()) {
				mapping.addStyleClass("maskLine");
			}
			if (hasIndexQuery(from) || hasIndexQuery(to)) {
				mapping.addStyleClass("indexQueryLine");
			}
			else {
				mapping.removeStyleClass("indexQueryLine");
			}
			return mapping;
		}
	}
	
	public static boolean hasIndexQuery(ParsedPath path) {
		if (path.getIndex() != null && !path.getIndex().matches("[0-9]+")) {
			return true;
		}
		return path.getChildPath() != null
			? hasIndexQuery(path.getChildPath())
			: false;
	}
			
	private final class StepMarshallable implements Marshallable<Step> {
		@Override
		public String marshal(Step step) {
			String specific = "";
			if (step instanceof For) {
				specific = " each " + ((For) step).getVariable() + " in " + ((For) step).getQuery();
			}
			else if (step instanceof Switch) {
				String query = ((Switch) step).getQuery();
				if (query != null) {
					specific = " on " + query;
				}
			}
			else if (step instanceof Throw) {
				if (((Throw) step).getCode() != null) {
					specific = " [" + ((Throw) step).getCode() + "]";
				}
				if (((Throw) step).getMessage() != null) {
					specific = ": " + ((Throw) step).getMessage();
				}
			}
			else if (step instanceof Sequence) {
				if (((Sequence) step).getStep() != null) {
					specific = ": " + ((Sequence) step).getStep();
				}
			}
			String label = step.getLabel() != null ? step.getLabel() + ": " : "";
			// if the label is empty inside a switch, it is the default option
			if (label.isEmpty() && step.getParent() instanceof Switch) {
				label = "$default: ";
			}
			String comment = step.getComment();
			if (comment == null && step instanceof Map) {
				Invoke invoke = null;
				for (Step child : ((Map) step).getChildren()) {
					if (child instanceof Invoke) {
						if (invoke == null) {
							invoke = (Invoke) child;
						}
						else {
							invoke = null;
							break;
						}
					}
				}
				if (invoke != null) {
					comment = invoke.getServiceId();
				}
			}
			return label + step.getClass().getSimpleName() + specific + (comment != null ? " (" + comment + ")" : "");
		}
	}

	private class ServiceAddHandler implements EventHandler<Event> {
		private Tree<Step> tree;
		private Class<? extends Step> step;
		
		public ServiceAddHandler(Tree<Step> tree, Class<? extends Step> step) {
			this.tree = tree;
			this.step = step;
		}

		@Override
		public void handle(Event arg0) {
			TreeCell<Step> selectedItem = tree.getSelectionModel().getSelectedItem();
			if (selectedItem != null) {
				// add an element in it
				if (selectedItem.getItem().itemProperty().get() instanceof StepGroup) {
					if (!(selectedItem.getItem().itemProperty().get() instanceof LimitedStepGroup)
							|| ((LimitedStepGroup) selectedItem.getItem().itemProperty().get()).getAllowedSteps().contains(step)) {
						try {
							Step instance = step.newInstance();
							instance.setParent((StepGroup) selectedItem.getItem().itemProperty().get());
							((StepGroup) selectedItem.getItem().itemProperty().get()).getChildren().add(instance);
							selectedItem.expandedProperty().set(true);
							MainController.getInstance().setChanged();
						}
						catch (InstantiationException e) {
							throw new RuntimeException(e);
						}
						catch (IllegalAccessException e) {
							throw new RuntimeException(e);
						}
					}
				}
				((StepTreeItem) selectedItem.getItem()).refresh();
				// add an element next to it
				// TODO
			}
		}
	}
	
	public static String getIcon(Class<? extends Step> clazz) {
		return "step/" + clazz.getSimpleName().toLowerCase() + ".png";
	}
	public static String getIcon(Step step) {
		return getIcon(step.getClass());
	}

	@Override
	public Class<VMService> getArtifactClass() {
		return getArtifactManager().getArtifactClass();
	}

	@Override
	public void display(MainController controller, AnchorPane pane, VMService artifact) throws IOException, ParseException {
		displayWithController(controller, pane, artifact);
	}

	@Override
	public void setConfiguration(java.util.Map<String, String> configuration) {
		this.configuration = configuration;
	}
	
	public boolean isInterfaceEditable() {
		return configuration == null || configuration.get(INTERFACE_EDITABLE) == null || configuration.get(INTERFACE_EDITABLE).equals("true");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean locate(Validation<?> validation) {
		if (!validation.getContext().isEmpty() && serviceTree != null) {
			if (validation.getContext().size() == 1 && validation.getContext().get(0) instanceof String) {
				TreeItem<Step> locate = locate(serviceTree.rootProperty().get(), ((String) validation.getContext().get(0)).replaceAll("^.*:", ""));
				if (locate != null) {
					TreeCell<Step> treeCell = serviceTree.getTreeCell(locate);
					treeCell.select();
					return true;
				}
			}
			else {
				List<?> context = new ArrayList(validation.getContext());
				Collections.reverse(context);
				return locate(serviceTree, serviceTree.rootProperty().get(), context, 0);
			}
		}
		return false;
	}
	
	private TreeItem<Step> locate(TreeItem<Step> item, String id) {
		if (item.itemProperty().get().getId().equals(id)) {
			return item;
		}
		for (TreeItem<Step> child : item.getChildren()) {
			TreeItem<Step> locate = locate(child, id);
			if (locate != null) {
				return locate;
			}
		}
		return null;
	}
	
	private boolean locate(Tree<Step> tree, TreeItem<Step> item, List<?> context, int counter) {
		Object object = context.get(counter);
		if (object instanceof String) {
			// the context is a description of the step, followed by a ":" and then the id
			String id = ((String) object).replaceAll("^.*:", "");
			if (item.itemProperty().get().getId().equals(id)) {
				// we still have to go deeper
				if (counter < context.size() - 1) {
					// in a map, check if it is a child step, if so, highlight it
					if (item.itemProperty().get() instanceof Map) {
						String childId = ((String) context.get(counter + 1)).replaceAll("^.*:", "");
						for (Step child : ((Map) item.itemProperty().get()).getChildren()) {
							if (childId.equals(child.getId())) {
								TreeCell<Step> treeCell = tree.getTreeCell(item);
								treeCell.select();
								return true;
							}
						}
					}
					else {
						for (TreeItem<Step> child : item.getChildren()) {
							boolean located = locate(tree, child, context, counter + 1);
							if (located) {
								return located;
							}
						}
					}
				}
				// we have found it
				else {
					tree.getTreeCell(item).select();
					return true;
				}
			}
		}
		return false;
	}

	public Tree<Step> getServiceTree() {
		return serviceTree;
	}

	public boolean isDisablePipelineEditing() {
		return disablePipelineEditing;
	}

	public void setDisablePipelineEditing(boolean disablePipelineEditing) {
		this.disablePipelineEditing = disablePipelineEditing;
	}
	
	// it is important enough to put in the main tree
//	@Override
//	public String getCategory() {
//		return "Services";
//	}
	
}
