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
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ArtifactGUIInstance;
import be.nabu.eai.developer.api.ConfigurableGUIManager;
import be.nabu.eai.developer.api.InterfaceLister;
import be.nabu.eai.developer.api.InterfaceLister.InterfaceDescription;
import be.nabu.eai.developer.api.PortableArtifactGUIManager;
import be.nabu.eai.developer.api.ValidationSelectableArtifactGUIManager;
import be.nabu.eai.developer.components.RepositoryBrowser;
import be.nabu.eai.developer.events.ArtifactMoveEvent;
import be.nabu.eai.developer.events.VariableRenameEvent;
import be.nabu.eai.developer.managers.ServiceGUIManager;
import be.nabu.eai.developer.managers.util.DoubleAmountListener;
import be.nabu.eai.developer.managers.util.ElementLineConnectListener;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.managers.util.MovablePane;
import be.nabu.eai.developer.managers.util.RootElementWithPush;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
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
import be.nabu.eai.module.services.vm.util.VMServiceController;
import be.nabu.eai.module.types.structure.StructureGUIManager;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ExtensibleEntry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.tree.Marshallable;
import be.nabu.jfx.control.tree.MovableTreeItem;
import be.nabu.jfx.control.tree.MovableTreeItem.Direction;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.jfx.control.tree.drag.TreeDragDrop;
import be.nabu.jfx.control.tree.drag.TreeDragListener;
import be.nabu.jfx.control.tree.drag.TreeDropListener;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.QueryPart;
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
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.base.ElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.ValidateProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class VMServiceGUIManager implements PortableArtifactGUIManager<VMService>, ConfigurableGUIManager<VMService>, ValidationSelectableArtifactGUIManager<VMService> {

	static {
		URL resource = VMServiceGUIManager.class.getClassLoader().getResource("vmservice.css");
		if (resource != null) {
			MainController.registerStyleSheet(resource.toExternalForm());
		}
	}
	
	public static List<Element<?>> removeUnusedElements(VMService service) throws ParseException {
		List<Element<?>> unusedPipelineElements = getUnusedPipelineElements(service);
		if (!unusedPipelineElements.isEmpty()) {
			for (Element<?> element : unusedPipelineElements) {
				service.getPipeline().remove(element);
				((ModifiableComplexType) service.getPipeline().get(Pipeline.INPUT).getType()).remove(element);
				((ModifiableComplexType) service.getPipeline().get(Pipeline.OUTPUT).getType()).remove(element);
			}
		}
		return unusedPipelineElements;
	}
	
	public static List<Element<?>> getUnusedPipelineElements(VMService service) throws ParseException {
		// initially add all elements, remove them if used
		List<Element<?>> elements = new ArrayList<Element<?>>(TypeUtils.getAllChildren(service.getPipeline()));
		// remove the inputs and outputs from that list
		Iterator<Element<?>> iterator = elements.iterator();
		while (iterator.hasNext()) {
			Element<?> next = iterator.next();
			if (next.getName().equals("input") || next.getName().equals("output")) {
				iterator.remove();
			}
		}
		List<Element<?>> inputs = new ArrayList<Element<?>>(TypeUtils.getAllChildren((ComplexType) service.getPipeline().get(Pipeline.INPUT).getType()));
		List<Element<?>> outputs = new ArrayList<Element<?>>(TypeUtils.getAllChildren((ComplexType) service.getPipeline().get(Pipeline.OUTPUT).getType()));
		removeUsedPipelineElements(elements, inputs, outputs, service.getRoot());
		// add the remaining inputs and outputs that are unused as well
		elements.addAll(inputs);
		elements.addAll(outputs);
		return elements;
	}
	
	private static void removeUsedPipelineElements(List<Element<?>> elements, List<Element<?>> inputs, List<Element<?>> outputs, Step step) throws ParseException {
		if (step instanceof Link) {
			if (((Link) step).isFixedValue() && ((Link) step).getFrom().startsWith("=")) {
				ParsedPath from = new ParsedPath(((Link) step).getFrom().substring(1));
				removeUsedPipelineElements(elements, inputs, outputs, from, true);
			}
			else if (!((Link) step).isFixedValue()) {
				ParsedPath from = new ParsedPath(((Link) step).getFrom());
				removeUsedPipelineElements(elements, inputs, outputs, from, true);
			}
			ParsedPath to = new ParsedPath(((Link) step).getTo());
			removeUsedPipelineElements(elements, inputs, outputs, to, true);
		}
		if (step.getLabel() != null) {
			removeUsedPipelineElements(elements, inputs, outputs, step.getLabel());
		}
		if (step instanceof For) {
			String query = ((For) step).getQuery();
			if (query != null) {
				removeUsedPipelineElements(elements, inputs, outputs, query);
			}
		}
		if (step instanceof Throw) {
			String message = ((Throw) step).getMessage();
			if (message != null && message.startsWith("=")) {
				removeUsedPipelineElements(elements, inputs, outputs, message.substring(1));
			}
		}
		if (step instanceof Switch) {
			String query = ((Switch) step).getQuery();
			if (query != null) {
				removeUsedPipelineElements(elements, inputs, outputs, query);
			}
		}
		// recurse
		if (step instanceof StepGroup) {
			for (Step child : ((StepGroup) step).getChildren()) {
				removeUsedPipelineElements(elements, inputs, outputs, child);
			}
		}
	}
	
	private static void removeUsedPipelineElements(List<Element<?>> elements, List<Element<?>> inputs, List<Element<?>> outputs, ParsedPath path, boolean isRoot) throws ParseException {
		// if we are at the root of the path, the first reference is important
		if (isRoot) {
			List<Element<?>> elementsToCheck;
			if (path.getName().equals("input")) {
				elementsToCheck = inputs;
				path = path.getChildPath();
			}
			else if (path.getName().equals("output")) {
				elementsToCheck = outputs;
				path = path.getChildPath();
			}
			else {
				elementsToCheck = elements;
			}
			Iterator<Element<?>> iterator = elementsToCheck.iterator();
			// check if the element is still in the list of unused, if so remove it
			while (iterator.hasNext()) {
				if (iterator.next().getName().equals(path.getName())) {
					iterator.remove();
					break;
				}
			}
		}
		if (path.getIndex() != null) {
			removeUsedPipelineElements(elements, inputs, outputs, path.getIndex());
		}
		if (path.getChildPath() != null) {
			removeUsedPipelineElements(elements, inputs, outputs, path.getChildPath(), false);
		}
	}

	private static void removeUsedPipelineElements(List<Element<?>> elements, List<Element<?>> inputs, List<Element<?>> outputs, String query) throws ParseException {
		List<QueryPart> parse = QueryParser.getInstance().parse(query);
		for (QueryPart part : parse) {
			if (part.getType() == QueryPart.Type.VARIABLE) {
				// if it starts with a "/", we have an absolute path
				removeUsedPipelineElements(elements, inputs, outputs, new ParsedPath(part.getToken().getContent()), part.getToken().getContent().startsWith("/"));
			}
		}
	}
	
	public static interface ServiceAcceptor {
		public boolean accept(String serviceId);
	}
	
	private ServiceAcceptor serviceInvokeAcceptor;
	private boolean disablePipelineEditing;
	
	private ObjectProperty<TreeCell<Element<?>>> lastSelectedInputElement = new SimpleObjectProperty<TreeCell<Element<?>>>();
	private ObjectProperty<TreeCell<Element<?>>> lastSelectedOutputElement = new SimpleObjectProperty<TreeCell<Element<?>>>();
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public static final String DATA_TYPE_STEP = "vmservice-step";
	private boolean removeInvalid = true;
	
	public static final String INTERFACE_EDITABLE = "interfaceEditable";
	private java.util.Map<String, String> configuration;
	
	public static final String ACTUAL_ID = "actualId";
	
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
	
	private ObservableList<Validation<?>> validations = FXCollections.observableArrayList();

	private Tree<Step> serviceTree;

	@Override
	public String getArtifactName() {
		return "Blox Service";
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
		service.setExecutorProvider(new RepositoryExecutorProvider(repository));
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
		
		String id = getId(service);
		Repository repository = EAIResourceRepository.getInstance();
		
		BooleanProperty locked = controller.hasLock(getId(service));
		BooleanBinding notLocked = locked.not();
		
		// the top part is the service, the bottom is a tabpane with input/output & mapping
		SplitPane splitPane = new SplitPane();
		
		AnchorPane top = new AnchorPane();
		splitPane.getItems().add(top);
//		serviceTree = new Tree<Step>(new StepMarshallable());
		serviceTree = new Tree<Step>(new StepFactory(validations));
		serviceTree.getStyleClass().add("serviceTree");
		
		validate(service);
		
		// if a variable is updated, reselect current step to redraw
		MainController.getInstance().getDispatcher().subscribe(VariableRenameEvent.class, new be.nabu.libs.events.api.EventHandler<VariableRenameEvent, Void>() {
			@Override
			public Void handle(VariableRenameEvent event) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						TreeCell<Step> selectedItem = serviceTree.getSelectionModel().getSelectedItem();
						// reselect the selected item to force a redraw in case of a map step
						if (selectedItem != null) {
							serviceTree.getSelectionModel().select(selectedItem);
						}
					}
				});
				return null;
			}
		});
		
		// if an artifact is moved, update the current instance as well (it is done in the repository but not in foreground)
		MainController.getInstance().getDispatcher().subscribe(ArtifactMoveEvent.class, new be.nabu.libs.events.api.EventHandler<ArtifactMoveEvent, Void>() {
			@Override
			public Void handle(ArtifactMoveEvent event) {
				Entry serviceEntry = controller.getRepository().getEntry(getId(service));
				if (serviceEntry != null) {
					// we get the new entry
					Entry entry = controller.getRepository().getEntry(event.getNewId());
					if (refactor(controller.getRepository().getReferences(serviceEntry.getId()), entry, event)) {
						MainController.getInstance().setChanged(serviceEntry.getId());
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								TreeCell<Step> selectedItem = serviceTree.getSelectionModel().getSelectedItem();
								// reselect the selected item to force a redraw in case of a map step
								if (selectedItem != null) {
									serviceTree.getSelectionModel().select(selectedItem);
								}
							}
						});
					}
				}
				return null;
			}
			private boolean refactor(List<String> references, Entry entry, ArtifactMoveEvent event) {
				boolean changed = false;
				try {
					if (references.contains(event.getOldId()) || references.contains(event.getNewId())) {
						new VMServiceManager().updateReference(service, event.getOldId(), event.getNewId());
						changed = true;
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				for (Entry child : entry) {
					changed |= refactor(references, child, event);
				}
				return changed;
			}
		});
		
		if (!service.isSupportsDescription()) {
			serviceController.getTabDescription().setDisable(true);
		}
		else {
			serviceController.getTxtDescription().setText(service.getDescription());
			serviceController.getTxtDescription().textProperty().addListener(new ChangeListener<String>() {
				@Override
				public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
					service.setDescription(newValue);
					MainController.getInstance().setChanged();
				}
			});
		}
		
		serviceTree.rootProperty().set(new StepTreeItem(service.getRoot(), null, false, locked));
		serviceTree.getRootCell().expandedProperty().set(true);
		// disable map tab
		serviceController.getTabMap().setDisable(true);
		
		serviceTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		serviceTree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Step>>() {
			@Override
			public void changed(ObservableValue<? extends TreeCell<Step>> arg0, TreeCell<Step> arg1, TreeCell<Step> arg2) {
				if (arg2 != null) {
					controller.showProperties(new StepPropertyProvider(arg2, repository, id));
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
					if (event.isShiftDown()) {
						selectedItem.collapseAll();
					}
					else {
						selectedItem.expandAll();
					}
				}
			}
		});

		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Sequence.class, notLocked));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Map.class, notLocked));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, For.class, notLocked));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Switch.class, notLocked));		
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Throw.class, notLocked));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Catch.class, notLocked));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Finally.class, notLocked));
		serviceController.getHbxButtons().getChildren().add(createAddButton(serviceTree, Break.class, notLocked));
		
		serviceController.getHbxButtons2().getChildren().add(createMoveButton(serviceTree, Direction.LEFT, notLocked));
		serviceController.getHbxButtons2().getChildren().add(createMoveButton(serviceTree, Direction.RIGHT, notLocked));
		serviceController.getHbxButtons2().getChildren().add(createMoveButton(serviceTree, Direction.UP, notLocked));
		serviceController.getHbxButtons2().getChildren().add(createMoveButton(serviceTree, Direction.DOWN, notLocked));
		
		serviceController.getHbxButtons().setPadding(new Insets(10));
		serviceController.getHbxButtons2().setPadding(new Insets(10));
		serviceController.getHbxButtons().setAlignment(Pos.TOP_CENTER);
		serviceController.getHbxButtons2().setAlignment(Pos.BOTTOM_CENTER);
		
		TextField search = new TextField();
		search.setPromptText("Search");
		search.setMinWidth(300);
		search.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				unhighlight();
				if (arg2 != null && !arg2.trim().isEmpty()) {
					highlight(arg2);
				}
			}
		});
		HBox searchBox = new HBox();
		Label searchLabel = new Label("Search: ");
		searchLabel.setPadding(new Insets(4, 10, 0, 5));
		searchBox.setPadding(new Insets(0, 0, 0, 10));
//		HBox filler = new HBox();
//		HBox.setHgrow(filler, Priority.ALWAYS);
		searchBox.setAlignment(Pos.TOP_RIGHT);
		searchBox.getChildren().addAll(search);
		HBox.setHgrow(searchBox, Priority.ALWAYS);
		
		serviceController.getHbxButtons().getChildren().add(searchBox);

		serviceController.getPanService().getChildren().add(serviceTree);
		
//		serviceTree.prefWidthProperty().bind(serviceController.getPanService().widthProperty());
		serviceTree.prefWidthProperty().bind(pane.widthProperty().subtract(25));
		
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
				return controller.hasLock().get() && arg0.getItem().getParent() != null;
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
				String serviceDataType = RepositoryBrowser.getDataType(DefinedService.class);
				// if we drop a service, wrap it in a map step
				if (serviceDataType.equals(dataType)) {
					if (target.getItem().itemProperty().get() instanceof StepGroup) {
						if (target.getItem().itemProperty().get() instanceof LimitedStepGroup) {
							return ((LimitedStepGroup) target.getItem().itemProperty().get()).getAllowedSteps().contains(Map.class);
						}
						else {
							return true;
						}
					}
				}
				else if (!dataType.equals(DATA_TYPE_STEP)) {
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
				String serviceDataType = RepositoryBrowser.getDataType(DefinedService.class);
				if (serviceDataType.equals(dataType)) {
					TreeCell<Entry> draggedEntry = (TreeCell<Entry>) dragged;
					Entry entry = draggedEntry.getItem().itemProperty().get();
					Map map = new Map();
					Invoke invoke = new Invoke();
					invoke.setServiceId(entry.getId());
					invoke.setParent(map);
					invoke.setX(30);
					invoke.setY(30);
					map.setParent(newParent);
					map.getChildren().add(invoke);
					newParent.getChildren().add(map);
					target.expandedProperty().set(true);
					((StepTreeItem) target.getItem()).refresh();
				}
				else {
					TreeCell<Step> draggedElement = (TreeCell<Step>) dragged;
					StepGroup originalParent = (StepGroup) draggedElement.getItem().getParent().itemProperty().get();
					if (originalParent.getChildren().remove(draggedElement.getItem().itemProperty().get())) {
						newParent.getChildren().add(draggedElement.getItem().itemProperty().get());
						draggedElement.getItem().itemProperty().get().setParent(newParent);
					}
					// refresh both
					((StepTreeItem) target.getItem()).refresh();
					((StepTreeItem) dragged.getParent().getItem()).refresh();
				}
				MainController.getInstance().setChanged();
			}
		});
		
		// show the input & output
		StructureGUIManager structureManager = new StructureGUIManager();
		structureManager.setActualId(getId(service));
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
		
//		serviceController.getTxtInterface().setDisable(!isInterfaceEditable());
//		serviceController.getHbxButtons().disableProperty().bind(notLocked);
		serviceController.getTxtInterface().disableProperty().bind(notLocked.or(new SimpleBooleanProperty(!isInterfaceEditable())));
		
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
		
		serviceController.getChkValidateInput().disableProperty().bind(notLocked);
		serviceController.getChkValidateOutput().disableProperty().bind(notLocked);
		
		serviceController.getTxtInterface().setPromptText("Defined Interface");
		serviceController.getBoxInterface().setPadding(new Insets(10, 20, 10, 20));
		serviceController.getChkValidateInput().setPadding(new Insets(14, 20, 10, 15));
		serviceController.getChkValidateOutput().setPadding(new Insets(14, 20, 10, 15));
		serviceController.getChkValidateOutput().setContentDisplay(ContentDisplay.RIGHT);
		
//		serviceController.getChkValidateInput().setStyle("-fx-border")
		
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
		
		leftTree = buildLeftPipeline(controller, serviceController, service.getRoot(), getId(service));
		rightTree = buildRightPipeline(controller, service, serviceTree, serviceController, service.getRoot(), getId(service));
		
		ContextMenu context = new ContextMenu();
		final Entry entry = controller.getRepository().getEntry(getId(service));
		if (entry != null && entry.getParent() instanceof ExtensibleEntry) {
			MenuItem item = new MenuItem("Extract to separate service");
			item.disableProperty().bind(notLocked);
			item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					// find good name
					int counter = 0;
					while(counter < 100) {
						if (entry.getParent().getChild("extracted" + counter) != null) {
							counter++;
						}
						else {
							break;
						}
					}
					// start with the same pipeline
					SimpleVMServiceDefinition definition = new SimpleVMServiceDefinition(service.getPipeline());
					Sequence root = new Sequence();
					for (TreeCell<Step> selected : serviceTree.getSelectionModel().getSelectedItems()) {
						root.getChildren().add(selected.getItem().itemProperty().get());
					}
					definition.setRoot(root);
					try {
						// save the new service
						ResourceEntry createNode = ((ExtensibleEntry) entry.getParent()).createNode("extracted" + counter, new VMServiceManager(), true);
						new VMServiceManager().save(createNode, definition);
						
						// we reload it to make sure we don't accidently modify the original pipeline that served as the basis
						VMService load = new VMServiceManager().load(createNode, new ArrayList<Validation<?>>());
						// remove all the unused elements
						removeUnusedElements(load);
						
						// reload repository
						MainController.getInstance().getRepository().reload(entry.getParent().getId());
						// trigger refresh in tree
						TreeItem<Entry> resolve = MainController.getInstance().getTree().resolve(entry.getParent().getId().replace('.', '/'), false);
						if (resolve != null) {
							resolve.refresh();
						}
						// reload remotely
						MainController.getInstance().getAsynchronousRemoteServer().reload(entry.getParent().getId());
						MainController.getInstance().getCollaborationClient().created(entry.getId(), "Extracted service");
//						MainController.getInstance().getServer().getRemote().reload(entry.getParent().getId());
						
						StepGroup lastParent = null;
						// remove the steps from the old service
						for (TreeCell<Step> selected : serviceTree.getSelectionModel().getSelectedItems()) {
							lastParent = selected.getItem().itemProperty().get().getParent();
							selected.getItem().itemProperty().get().getParent().getChildren().remove(selected.getItem().itemProperty().get());
						}
						
						// add a new map step with an invoke in it for the newly created service
						Map map = new Map();
						map.setParent(lastParent);
						Invoke invoke = new Invoke();
						invoke.setServiceId(createNode.getId());
						invoke.setParent(map);
						map.getChildren().add(invoke);
						lastParent.getChildren().add(map);
						serviceTree.refresh();
						MainController.getInstance().setChanged();
						
						// clean up the pipeline of the old service
						removeUnusedElements(service);
						
						// open it in a new tab
						RepositoryBrowser.open(MainController.getInstance(), createNode);
						// refocus on refactored service
						MainController.getInstance().activate(entry.getId());
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}
				}
			});
			item.disableProperty().bind(serviceTree.getSelectionModel().selectedItemProperty().isNull());
			context.getItems().add(item);
		}
		
		if (!context.getItems().isEmpty()) {
			serviceTree.setContextMenu(context);
		}

		// if we select the interface tab, we want to refresh the input & output trees as changes may have been done in the map view
		// note that we _can't_ add the interface trees as refreshables to the pipeline trees as they are already coupled the other way around
		// any coupling in both directions ends with an infinite refresh cycle
		serviceController.getTabMap().getTabPane().getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
			@Override
			public void changed(ObservableValue<? extends Tab> arg0, Tab arg1, Tab arg2) {
				if (serviceController.getTabInterface().equals(arg2)) {
					inputTree.refresh();
					outputTree.refresh();
				}
			}
		});
		
		serviceTree.setClipboardHandler(new StepClipboardHandler(serviceTree));
		// if we select a map step, we have to show the mapping screen
		serviceTree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Step>>() {
			@Override
			public void changed(ObservableValue<? extends TreeCell<Step>> arg0, TreeCell<Step> arg1, TreeCell<Step> arg2) {
				if (arg2 != null) {
					Step step = arg2.getItem().itemProperty().get();
					
					// refresh the step
					arg2.getItem().itemProperty().get().refresh();
					
					// enable/disable buttons depending on the selection
					// first just disable all buttons
//					for (Button button : addButtons.values()) {
//						button.disableProperty().set(true);
//					}
					// for a stepgroup, reenable some or all buttons
//					if (step instanceof StepGroup) {
//						for (Class<? extends Step> supported : step instanceof LimitedStepGroup ? ((LimitedStepGroup) step).getAllowedSteps() : addButtons.keySet()) {
//							if (addButtons.containsKey(supported)) {
////								addButtons.get(supported).disableProperty().set(false);
//							}
//						}
//					}
					
					// if the new selection is not a map, or not the same map, clear it
					if (!(step instanceof Map) || !arg2.equals(arg1)) {
						serviceController.getTabMap().setDisable(true);
						// select interface pane
						serviceController.getTabMap().getTabPane().getSelectionModel().select(serviceController.getTabInterface());
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
						// reset the last selected element
						lastSelectedInputElement.set(null);
						lastSelectedOutputElement.set(null);
						
						leftTree = buildLeftPipeline(controller, serviceController, (Map) arg2.getItem().itemProperty().get(), getId(service));
						rightTree = buildRightPipeline(controller, service, serviceTree, serviceController, (Map) arg2.getItem().itemProperty().get(), getId(service));
						
						// resize
						serviceController.getPanLeft().minWidthProperty().set(50);
						serviceController.getPanRight().minWidthProperty().set(50);
						serviceController.getPanLeft().prefWidthProperty().set(100);
						serviceController.getPanRight().prefWidthProperty().set(100);
						
						serviceController.getTabMap().setDisable(false);

						// make sure we select the map tab because you probably want to edit that...
						serviceController.getTabMap().getTabPane().getSelectionModel().select(serviceController.getTabMap());
						
						// first draw all the invokes and build a map of temporary result mappings
						invokeWrappers = new HashMap<String, InvokeWrapper>();
						for (final Step child : ((Map) arg2.getItem().itemProperty().get()).getChildren()) {
							if (child instanceof Invoke) {
								drawInvoke(controller, (Invoke) child, invokeWrappers, serviceController, service, serviceTree, locked, repository, id);
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
											invokeWrappers,
											locked
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
													controller.showProperties(new LinkPropertyUpdater(link, mapping, repository, id));
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
									Mapping mapping = buildMapping(link, serviceController.getPanMap(), leftTree, rightTree, invokeWrappers, locked);
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
												controller.showProperties(new LinkPropertyUpdater(link, mapping, repository, id));
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
			}

		});
		
		// the service controller resizes the scroll pane based on this pane
		// so bind it to the the tree
		
//		serviceController.getPanLeft().prefWidthProperty().bind(leftTree.widthProperty());
//		serviceController.getPanRight().prefWidthProperty().bind(rightTree.widthProperty());
		
		EventHandler<DragEvent> serviceDragOverHandler = new EventHandler<DragEvent>() {
			@Override
			public void handle(DragEvent event) {
				if (serviceTree.getSelectionModel().getSelectedItem() == null || serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get() instanceof Map) {
					Dragboard dragboard = event.getDragboard();
					if (dragboard != null) {
						Object content = dragboard.getContent(TreeDragDrop.getDataFormat(RepositoryBrowser.getDataType(DefinedService.class)));
						// this will be the path in the tree
						if (content != null) {
							String serviceId = controller.getRepositoryBrowser().getControl().resolve((String) content).itemProperty().get().getId();
							if (serviceId != null && (serviceInvokeAcceptor == null || serviceInvokeAcceptor.accept(serviceId))) {
								event.acceptTransferModes(TransferMode.MOVE);
								event.consume();
							}
						}
					}
				}
			}
		};
		serviceController.getPanMiddle().addEventHandler(DragEvent.DRAG_OVER, serviceDragOverHandler);
		serviceTree.addEventHandler(DragEvent.DRAG_OVER, serviceDragOverHandler);		
		
		EventHandler<DragEvent> serviceDragDropHandler = new EventHandler<DragEvent>() {
			@Override
			public void handle(DragEvent event) {
				Dragboard dragboard = event.getDragboard();
				if (dragboard != null && !event.isDropCompleted() && !event.isConsumed()) {
					Object content = dragboard.getContent(TreeDragDrop.getDataFormat(RepositoryBrowser.getDataType(DefinedService.class)));
					// this will be the path in the tree
					if (content != null) {
						String serviceId = controller.getRepositoryBrowser().getControl().resolve((String) content).itemProperty().get().getId();
						if (serviceId != null && (serviceInvokeAcceptor == null || serviceInvokeAcceptor.accept(serviceId))) {
							Step step = serviceTree.getSelectionModel().getSelectedItem() == null ? serviceTree.rootProperty().get().itemProperty().get() : serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get();
							if (!(step instanceof Map) && step instanceof StepGroup) {
								if (!(step instanceof LimitedStepGroup)
										|| ((LimitedStepGroup) step).getAllowedSteps().contains(Map.class)) {
									Map map = new Map();
									map.setParent((StepGroup) step);
									((StepGroup) step).getChildren().add(map);
									step = map;
									if (serviceTree.getSelectionModel().getSelectedItem() != null) {
										serviceTree.getSelectionModel().getSelectedItem().expandedProperty().set(true);
										serviceTree.getSelectionModel().getSelectedItem().getItem().refresh();
									}
									else {
										serviceTree.getTreeCell(serviceTree.rootProperty().get()).expandedProperty().set(true);
										serviceTree.rootProperty().get().refresh();
									}
								}
							}
							if (step instanceof Map) {
								Map target = (Map) step;

								Invoke invoke = new Invoke();
								invoke.setParent(target);
								invoke.setServiceId(serviceId);
								// the position is relative to where you dropped it, not the scene!
//									invoke.setX(event.getSceneX());
//									invoke.setY(event.getSceneY());
								invoke.setX(event.getX());
								invoke.setY(event.getY());
								target.getChildren().add(invoke);
								if (serviceTree.getSelectionModel().getSelectedItem() != null) {
									drawInvoke(controller, invoke, invokeWrappers, serviceController, service, serviceTree, locked, repository, id);
									serviceTree.getSelectionModel().getSelectedItem().refresh();
								}
								else {
									serviceTree.getTreeCell(serviceTree.rootProperty().get()).refresh();
								}
								MainController.getInstance().setChanged();
								MainController.getInstance().closeDragSource();
								event.setDropCompleted(true);
								event.consume();
							}
						}
					}
				}
			}
		};
		serviceController.getPanMiddle().addEventHandler(DragEvent.DRAG_DROPPED, serviceDragDropHandler);
		serviceTree.addEventHandler(DragEvent.DRAG_DROPPED, serviceDragDropHandler);
		
		serviceController.getPanMap().addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (locked.get() && event.getCode() == KeyCode.D && event.isControlDown() && lastSelectedInputElement.get() != null && lastSelectedOutputElement.get() != null) {
					link(service, serviceController, lastSelectedInputElement.get(), lastSelectedOutputElement.get(), event.isShiftDown(), locked, false, repository, id);
				}
				else if (locked.get() && event.getCode() == KeyCode.D && event.isAltDown() && lastSelectedInputElement.get() != null && lastSelectedOutputElement.get() != null) {
					link(service, serviceController, lastSelectedInputElement.get(), lastSelectedOutputElement.get(), event.isShiftDown(), locked, true, repository, id);
				}
			}
		});
		return serviceController;
	}

	void validate(final VMService service) {
		List<Validation<?>> validate = service.getRoot().validate(EAIResourceRepository.getInstance().getServiceContext());
		validations.clear();
		if (validate != null && validate.size() > 0) {
			validations.addAll(validate);
		}
		makeIdsUnique(service.getRoot(), new ArrayList<String>());
	}
	
	void makeIdsUnique(StepGroup group, List<String> ids) {
		if (group.getChildren() != null) {
			for (Step child : group.getChildren()) {
				if (ids.indexOf(child.getId()) >= 0) {
					child.setId(UUID.randomUUID().toString().replace("-", ""));
					MainController.getInstance().setChanged();
				}
				else {
					ids.add(child.getId());
				}
				if (child instanceof StepGroup) {
					makeIdsUnique((StepGroup) child, ids);
				}
			}
		}
	}
	
	public void unhighlight() {
		unhighlight(serviceTree.rootProperty().get());
	}
	
	public void unhighlight(TreeItem<Step> item) {
		TreeCell<Step> cell = serviceTree.getTreeCell(item);
		cell.getCellValue().getNode().getStyleClass().remove("highlightedStep");
		// don't collapse the root but collapse everything else so we only have expanded that which has a match afterwards
		if (cell.getParent() != null && cell.expandedProperty().get()) {
			cell.expandedProperty().set(false);
		}
		if (item.getChildren() != null && !item.getChildren().isEmpty()) {
			for (TreeItem<Step> child : item.getChildren()) {
				unhighlight(child);
			}
		}
	}
	
	public void highlight(String text) {
		highlight(serviceTree.rootProperty().get(), text);
	}
	
	private void highlight(TreeItem<Step> item, String text) {
		TreeCell<Step> cell = serviceTree.getTreeCell(item);
		cell.getCellValue().getNode().getStyleClass().remove("highlightedStep");
		if (text != null && !text.trim().isEmpty() && matches(item.itemProperty().get(), text, false)) {
			if (!cell.getCellValue().getNode().getStyleClass().contains("highlightedStep")) {
				cell.getCellValue().getNode().getStyleClass().add("highlightedStep");
			}
			cell.show();
		}
		if (item.getChildren() != null && !item.getChildren().isEmpty()) {
			for (TreeItem<Step> child : item.getChildren()) {
				highlight(child, text);
			}
		}
		// children but none of them are shown in the tree
		else if (text != null && !text.trim().isEmpty() && item.itemProperty().get() instanceof StepGroup && matches(item.itemProperty().get(), text, true)) {
			if (!cell.getCellValue().getNode().getStyleClass().contains("highlightedStep")) {
				cell.getCellValue().getNode().getStyleClass().add("highlightedStep");
			}
			cell.show();
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
		else if (step.getLabel() != null && step.getLabel().matches(regex)) {
			return true;
		}
		else if (step instanceof For) {
			if (((For) step).getQuery() != null && ((For) step).getQuery().matches(regex)) {
				return true;
			}
			else if (((For) step).getVariable() != null && ((For) step).getVariable().matches(regex)) {
				return true;
			}
		}
		else if (step instanceof Sequence) {
			if (((Sequence) step).getTransactionVariable() != null && ((Sequence) step).getTransactionVariable().matches(regex)) {
				return true;
			}
		}
		else if (step instanceof Link) {
			if (((Link) step).getFrom() != null && ((Link) step).getFrom().matches(regex)) {
				return true;
			}
			else if (((Link) step).getTo() != null && ((Link) step).getTo().matches(regex)) {
				return true;
			}
		}
		else if (step instanceof Invoke) {
			if (((Invoke) step).getServiceId() != null && ((Invoke) step).getServiceId().matches(regex)) {
				return true;
			}
		}
		else if (step instanceof Throw) {
			if (((Throw) step).getCode() != null && ((Throw) step).getCode().matches(regex)) {
				return true;
			}
			else if (((Throw) step).getMessage() != null && ((Throw) step).getMessage().matches(regex)) {
				return true;
			}
		}
		else if (step instanceof Switch) {
			if (((Switch) step).getQuery() != null && ((Switch) step).getQuery().matches(regex)) {
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
	
	private InvokeWrapper drawInvoke(MainController controller, final Invoke invoke, java.util.Map<String, InvokeWrapper> invokeWrappers, VMServiceController serviceController, VMService service, Tree<Step> serviceTree, ReadOnlyBooleanProperty lock, Repository repository, String id) {
		InvokeWrapper invokeWrapper = new InvokeWrapper(controller, invoke, serviceController.getPanMiddle(), service, serviceController, serviceTree, mappings, lock, repository, id);
		invokeWrappers.put(invoke.getResultName(), invokeWrapper);
		Pane pane = invokeWrapper.getComponent();
		serviceController.getPanMiddle().getChildren().add(pane);
		BooleanProperty locked = controller.hasLock(getId(service));
		MovablePane movable = MovablePane.makeMovable(pane, locked);
//		movable.setGridSize(10);
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
		FixedValue.allowFixedValue(controller, fixedValues, serviceTree, invokeWrapper.getInput(), repository, id);
		
		// we can draw lines from it
		invokeWrapper.getOutput().getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Element<?>>>() {
			@Override
			public void changed(ObservableValue<? extends TreeCell<Element<?>>> arg0, TreeCell<Element<?>> arg1, TreeCell<Element<?>> arg2) {
				if (arg2 != null) {
					lastSelectedInputElement.set(arg2);
				}
			}
		});
		invokeWrapper.getInput().getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Element<?>>>() {
			@Override
			public void changed(ObservableValue<? extends TreeCell<Element<?>>> arg0, TreeCell<Element<?>> arg1, TreeCell<Element<?>> arg2) {
				if (arg2 != null) {
					lastSelectedOutputElement.set(arg2);
				}
			}
		});
		
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
	
	private Button createAddButton(Tree<Step> serviceTree, Class<? extends Step> clazz, BooleanBinding notLocked) {
		Button button = new Button();
		button.setTooltip(new Tooltip(clazz.getSimpleName()));
		button.setGraphic(MainController.loadFixedSizeGraphic(getIcon(clazz)));
		button.addEventHandler(ActionEvent.ACTION, new ServiceAddHandler(serviceTree, clazz));
		button.disableProperty().bind(notLocked);
		addButtons.put(clazz, button);
		return button;
	}
	
	private Node createMoveButton(Tree<Step> serviceTree, Direction direction, BooleanBinding notLocked) {
		Button button = new Button();
		button.setTooltip(new Tooltip(direction.name()));
		button.setGraphic(MainController.loadFixedSizeGraphic("move/" + direction.name().toLowerCase() + ".png", 12));
		button.disableProperty().bind(notLocked);
		button.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				TreeCell<Step> cell = serviceTree.getSelectionModel().getSelectedItem();
				if (cell != null) {
					TreeItem<Step> item = cell.getItem();
					((MovableTreeItem<?>) item).move(direction);
				}
			}
		});
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
									
									// if we are creating a new drop, remove any links to the target
									iterator = step.getChildren().iterator();
									while (iterator.hasNext()) {
										Step child = iterator.next();
										// if there is a link to the child
										if (child instanceof Link && path.equals(((Link) child).getTo())) {
											if (mappings.containsKey(child)) {
												mappings.get(child).remove();
												mappings.remove(child);
												iterator.remove();
											}
											else if (fixedValues.containsKey(child)) {
												fixedValues.get(child).remove();
												fixedValues.remove(child);
												iterator.remove();
											}
										}
									}
									
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
	
	private void link(VMService service, VMServiceController serviceController, TreeCell<Element<?>> from, TreeCell<Element<?>> to, boolean mask, ReadOnlyBooleanProperty lock, boolean recursiveMapping, Repository repository, String id) {
		boolean alreadyMapped = false;
		for (Mapping mapping : mappings.values()) {
			if (mapping.getFrom().equals(from) && mapping.getTo().equals(to)) {
				alreadyMapped = true;
				break;
			}
		}
		if (!alreadyMapped) {
			Element<?> fromElement = from.getItem().itemProperty().get();
			Element<?> toElement = to.getItem().itemProperty().get();
			// if we can straight map it, do that
			if ((service.isMappable(fromElement, toElement) || (fromElement.getType() instanceof ComplexType && toElement.getType() instanceof ComplexType && mask)) && !recursiveMapping) {
				DropLinkListener.drawMapping(serviceController, serviceTree, mappings, to, from, mask, lock, repository, id);
			}
			// if they are both complex types, we can do a best effort mapping by name
			else if (fromElement.getType() instanceof ComplexType && toElement.getType() instanceof ComplexType) {
				Confirm.confirm(ConfirmType.QUESTION, "Map recursively", "Should a recursive mapping be attempted for '" + fromElement.getName() + "'?", new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						java.util.Map<String, TreeCell<Element<?>>> toFields = new HashMap<String, TreeCell<Element<?>>>();
						for (TreeItem<Element<?>> child : to.getItem().getChildren()) {
							TreeCell<Element<?>> cell = to.getCell(child);
							if (cell != null) {
								toFields.put(child.itemProperty().get().getName(), cell);
							}
						}
						for (TreeItem<Element<?>> child : from.getItem().getChildren()) {
							TreeCell<Element<?>> cell = from.getCell(child);
							if (cell != null) {
								TreeCell<Element<?>> target = toFields.get(child.itemProperty().get().getName());
								if (target != null) {
									link(service, serviceController, cell, target, mask, lock, false, repository, id);
								}
							}
						}
					}
				});
			}
		}
	}
	
	private Tree<Element<?>> buildRightPipeline(MainController controller, VMService service, Tree<Step> serviceTree, VMServiceController serviceController, StepGroup step, String actualId) {
		// remove listeners
		if (rightTree != null) {
			inputTree.removeRefreshListener(rightTree.getTreeCell(rightTree.rootProperty().get()));
			outputTree.removeRefreshListener(rightTree.getTreeCell(rightTree.rootProperty().get()));
		}
		final VBox right = new VBox();
		
		StructureGUIManager structureManager = new StructureGUIManager();
		structureManager.setActualId(actualId);
		try {
			// drop button was added afterwards, hence the mess
			DropButton dropButton = new DropButton(step);
			// this button was added even later!
			Button linkButton = new Button();
			linkButton.setGraphic(MainController.loadGraphic(getIcon(Map.class)));
			linkButton.disableProperty().bind(lastSelectedInputElement.isNull().or(lastSelectedOutputElement.isNull()));
			linkButton.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent arg0) {
					link(service, serviceController, lastSelectedInputElement.get(), lastSelectedOutputElement.get(), arg0.getButton() == MouseButton.SECONDARY, controller.hasLock(actualId), false, controller.getRepository(), actualId);
				}
			});
			
			Tree<Element<?>> rightTree = structureManager.display(controller, right, new RootElementWithPush(
				(Structure) step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()), 
				false,
				step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()).getProperties()
			), !disablePipelineEditing, false, linkButton, dropButton);
			dropButton.setTree(rightTree);
			
			rightTree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Element<?>>>() {
				@Override
				public void changed(ObservableValue<? extends TreeCell<Element<?>>> arg0, TreeCell<Element<?>> arg1, TreeCell<Element<?>> arg2) {
					if (arg2 != null) {
						lastSelectedOutputElement.set(arg2);
					}
				}
			});

			if (!disablePipelineEditing) {
				((ElementTreeItem) rightTree.rootProperty().get()).editableProperty().set(true);
				((ElementTreeItem) rightTree.rootProperty().get()).setShallowAllowNonLocalModification(true);
				((ElementImpl<?>) rightTree.rootProperty().get().itemProperty().get()).getBlockedProperties().addAll(rightTree.rootProperty().get().itemProperty().get().getSupportedProperties());
				// make sure the "input" & "output" are not editable
				for (TreeItem<Element<?>> item : rightTree.rootProperty().get().getChildren()) {
					if (item.itemProperty().get().getName().equals("input") || item.itemProperty().get().getName().equals("output")) {
						// simply block all properties
						if (isInterfaceEditable()) {
							((ElementImpl<?>) item.itemProperty().get()).getBlockedProperties().addAll(item.itemProperty().get().getSupportedProperties());
							for (TreeItem<Element<?>> child : item.getChildren()) {
								if (TypeUtils.getLocalChild((ComplexType) item.itemProperty().get().getType(), child.getName()) != null) {
									((ElementTreeItem) child).setEditable(true);
									((ElementTreeItem) child).setAllowNonLocalModification(false);
								}
								else {
									((ElementTreeItem) child).setEditable(false);
								}
							}
						}
						else {
							((ElementTreeItem) item).setEditable(false);
						}
					}
					else {
						item.editableProperty().set(true);
					}
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
			
			// make sure the left tree is refreshed if you add something to the right tree
			rightTree.addRefreshListener(leftTree.getTreeCell(leftTree.rootProperty().get()));
			
			TreeDragDrop.makeDroppable(rightTree, new DropLinkListener(controller, mappings, service, serviceController, serviceTree, controller.hasLock(actualId), controller.getRepository(), actualId));
			FixedValue.allowFixedValue(controller, fixedValues, serviceTree, rightTree, controller.getRepository(), actualId);
			
			rightTree.setClipboardHandler(new ElementClipboardHandler(rightTree));
			
			ContextMenu menu = new ContextMenu();
			MenuItem removeUnused = new MenuItem("Remove Unused Variables");
			removeUnused.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					try {
						removeUnusedElements(service);
						rightTree.getRootCell().refresh();
						MainController.getInstance().setChanged();
					}
					catch (ParseException e) {
						MainController.getInstance().notify(e);
					}
				}
			});
			menu.getItems().add(removeUnused);
			rightTree.setContextMenu(menu);
			return rightTree;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	private Tree<Element<?>> buildLeftPipeline(MainController controller, VMServiceController serviceController, StepGroup step, String actualId) {
		if (leftTree != null) {
			inputTree.removeRefreshListener(leftTree.getTreeCell(leftTree.rootProperty().get()));
			outputTree.removeRefreshListener(leftTree.getTreeCell(leftTree.rootProperty().get()));
		}
		Tree<Element<?>> leftTree = new Tree<Element<?>>(new ElementMarshallable());
		EAIDeveloperUtils.addElementExpansionHandler(leftTree);
		leftTree.rootProperty().set(new ElementTreeItem(new RootElementWithPush(
			(Structure) step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()),
			false,
			step.getPipeline(new SimpleExecutionContext.SimpleServiceContext()).getProperties()
		), null, false, false));
		// show properties if selected
		ElementSelectionListener elementSelectionListener = new ElementSelectionListener(controller, false);
		elementSelectionListener.setActualId(actualId);
		leftTree.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
		
		leftTree.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeCell<Element<?>>>() {
			@Override
			public void changed(ObservableValue<? extends TreeCell<Element<?>>> arg0, TreeCell<Element<?>> arg1, TreeCell<Element<?>> arg2) {
				if (arg2 != null) {
					lastSelectedInputElement.set(arg2);
				}
			}
		});
		
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
		leftTree.setReadOnly(true);
		
		// expand explicitly
		leftTree.getRootCell().expandedProperty().set(true);
		
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
		return new FixedValue(controller, tree.getTreeCell(target), link, controller.getRepository(), actualId);
	}
	
	private Mapping buildMapping(Link link, Pane target, Tree<Element<?>> left, Tree<Element<?>> right, java.util.Map<String, InvokeWrapper> invokeWrappers, ReadOnlyBooleanProperty lock) {
		ParsedPath from = new ParsedPath(link.getFrom());
		TreeItem<Element<?>> fromElement;
		Tree<Element<?>> fromTree;
		// this means you are mapping it from another invoke, use that output tree to find the element
		if (invokeWrappers.containsKey(from.getName())) {
			fromElement = from.getChildPath() != null ? find(invokeWrappers.get(from.getName()).getOutput().rootProperty().get(), from.getChildPath()) : invokeWrappers.get(from.getName()).getOutput().rootProperty().get();
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
//			Mapping mapping = new Mapping(target, fromTree.getTreeCell(fromElement), toTree.getTreeCell(toElement));
			Mapping mapping = buildMapping(link, target, fromTree.getTreeCell(fromElement), toTree.getTreeCell(toElement), lock);
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
	
	public static Mapping buildMapping(final Link link, Pane target, TreeCell<Element<?>> from, TreeCell<Element<?>> to, ReadOnlyBooleanProperty lock) {
		Mapping mapping = new Mapping(target, from, to, lock);
		mapping.getShape().addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getCode() == KeyCode.F8 && lock.get()) {
					if (!link.isFixedValue()) {
						boolean fromIsList = from.getItem().itemProperty().get().getType().isList(from.getItem().itemProperty().get().getProperties());
						ParsedPath path = new ParsedPath(link.getFrom());

						// not pretty...
						boolean fromInvoke = path.getName().matches("result[a-f0-9]{32}");
						DropLinkListener.setDefaultIndexes(fromInvoke ? path.getChildPath() : path, from.getTree().rootProperty().get(), fromIsList);
						if (!path.toString().equals(link.getFrom())) {
							link.setFrom(path.toString());
							MainController.getInstance().setChanged();
						}
					}
					
					boolean toIsList = to.getItem().itemProperty().get().getType().isList(to.getItem().itemProperty().get().getProperties());
					ParsedPath path = new ParsedPath(link.getTo());
					DropLinkListener.setDefaultIndexes(path, to.getTree().rootProperty().get(), toIsList);
					if (!path.toString().equals(link.getTo())) {
						link.setTo(path.toString());
						MainController.getInstance().setChanged();
					}
				}
			}
		});
		return mapping;
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
			return label + (step.getName() == null ? step.getClass().getSimpleName() : step.getName()) + specific + (comment != null ? " (" + comment + ")" : "");
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
	
	public String getId(VMService service) {
		return configuration == null || configuration.get(ACTUAL_ID) == null ? (actualId == null ? service.getId() : actualId) : configuration.get(ACTUAL_ID);
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
	
	private String actualId;

	public String isActualId() {
		return actualId;
	}

	public void setActualId(String actualId) {
		this.actualId = actualId;
	}
	
	// it is important enough to put in the main tree
//	@Override
//	public String getCategory() {
//		return "Services";
//	}
	
}
