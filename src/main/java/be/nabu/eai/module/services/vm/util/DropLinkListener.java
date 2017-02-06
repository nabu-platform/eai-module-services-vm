package be.nabu.eai.module.services.vm.util;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.controllers.VMServiceController;
import be.nabu.eai.module.services.vm.util.Mapping.RemoveMapping;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.jfx.control.tree.drag.TreeDragDrop;
import be.nabu.jfx.control.tree.drag.TreeDropListener;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;

public class DropLinkListener implements TreeDropListener<Element<?>> {
	private final VMService service;
	private final VMServiceController serviceController;
	private final Tree<Step> serviceTree;
	private java.util.Map<Link, Mapping> mappings;
	private MainController controller;

	public DropLinkListener(MainController controller, java.util.Map<Link, Mapping> mappings, VMService service, VMServiceController serviceController, Tree<Step> serviceTree) {
		this.controller = controller;
		this.mappings = mappings;
		this.service = service;
		this.serviceController = serviceController;
		this.serviceTree = serviceTree;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean canDrop(String dataType, TreeCell<Element<?>> target, TreeCell<?> dragged, TransferMode transferMode) {
		// this listener is only interested in link attempts (drawing a line)
		if (transferMode != TransferMode.LINK) {
			return false;
		}
		else if (!dataType.equals("type")) {
			return false;
		}
		else {
			TreeCell<Element<?>> draggedElement = (TreeCell<Element<?>>) dragged;
			Element<?> fromItem = draggedElement.getItem().itemProperty().get();
			Element<?> toItem = target.getItem().itemProperty().get();
			return service.isMappable(fromItem, toItem) || (fromItem.getType() instanceof ComplexType && toItem.getType() instanceof ComplexType && MainController.getInstance().isKeyActive(KeyCode.CONTROL));
		}
	}

	@Override
	public void drop(String arg0, TreeCell<Element<?>> target, TreeCell<?> dragged, TransferMode transferMode) {
		boolean alreadyMapped = false;
		for (Mapping mapping : mappings.values()) {
			if (mapping.getFrom().equals(dragged) && mapping.getTo().equals(target)) {
				alreadyMapped = true;
				break;
			}
		}
		if (!alreadyMapped) {
			draw(target, dragged);
		}
	}
	
	private void draw(TreeCell<Element<?>> target, TreeCell<?> dragged) {
		drawMapping(serviceController, serviceTree, mappings, target, dragged, MainController.getInstance().isKeyActive(KeyCode.CONTROL));
	}

	@SuppressWarnings("unchecked")
	public static void drawMapping(VMServiceController serviceController, Tree<Step> serviceTree, java.util.Map<Link, Mapping> mappings, TreeCell<Element<?>> target, TreeCell<?> dragged, boolean mask) {
		Mapping mapping = new Mapping(serviceController.getPanMap(), (TreeCell<Element<?>>) dragged, target);
		ParsedPath from = new ParsedPath(TreeDragDrop.getPath(dragged.getItem()));
		Invoke sourceInvoke = null;
		// you are dragging something from an invoke output
		if (dragged.getTree().get("invoke") != null) {
			// it has to come from the output
			if (!from.getName().equals("output")) {
				throw new RuntimeException("Expecting an output path");	
			}
			// update the unnecessary "output" with the actual name of the invoke as it is mapped to the pipeline
			from.setName(((Invoke) dragged.getTree().get("invoke")).getResultName());
			sourceInvoke = ((Invoke) dragged.getTree().get("invoke"));
		}
		else {
			if (!from.getName().equals("pipeline")) {
				throw new RuntimeException("Expecting a pipeline path");
			}
			else if (from.getChildPath() == null) {
				throw new RuntimeException("Can not drag the entire pipeline");
			}
			from = from.getChildPath();
		}
		ParsedPath to = new ParsedPath(TreeDragDrop.getPath(target.getItem()));
		// you are dragging it to an invoke input
		if (target.getTree().get("invoke") != null) {
			if (!to.getName().equals("input")) {
				throw new RuntimeException("Expecting an input path");
			}
			// don't need the "input" leadin
			to = to.getChildPath();
		}
		else {
			if (!to.getName().equals("pipeline")) {
				throw new RuntimeException("Expecting a pipeline path");
			}
			// don't need the "pipeline" bit
			to = to.getChildPath();
		}
		boolean toIsList = target.getItem().itemProperty().get().getType().isList(target.getItem().itemProperty().get().getProperties());
		boolean fromIsList = ((TreeItem<Element<?>>) dragged.getItem()).itemProperty().get().getType().isList(
			((TreeItem<Element<?>>) dragged.getItem()).itemProperty().get().getProperties());
		// if there is a source invoke, we keep the root element as it refers to the pipeline
		// however, for resolving purposes we don't want to use it
		if (sourceInvoke != null) {
			setDefaultIndexes(from.getChildPath(), (TreeItem<Element<?>>) dragged.getTree().rootProperty().get(), !toIsList);
		}
		else {
			setDefaultIndexes(from, (TreeItem<Element<?>>) dragged.getTree().rootProperty().get(), !toIsList);
		}
		setDefaultIndexes(to, target.getTree().rootProperty().get(), !fromIsList);
		final Link link = new Link(from.toString(), to.toString());
		link.setMask(mask);
		if (link.isMask()) {
			mapping.addStyleClass("maskLine");
		}
		// if the target is an invoke, the mapping has to be done inside the invoke
		if (target.getTree().get("invoke") != null) {
			link.setParent(((Invoke) target.getTree().get("invoke")));
			Invoke targetInvoke = (Invoke) target.getTree().get("invoke");
			if (sourceInvoke != null && sourceInvoke.getInvocationOrder() >= targetInvoke.getInvocationOrder()) {
				targetInvoke.setInvocationOrder(sourceInvoke.getInvocationOrder() + 1);
			}
			// add the link to the currently selected mapping
			targetInvoke.getChildren().add(link);
			// when you are mapping an input to an invoke, we also have to recalculate the invocation order for the mapping
			// it could be that you are mapping from another invoke which means this one has to be invoked after that
			MainController.getInstance().notify(((Map) serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get()).calculateInvocationOrder());
		}
		// else link it to the map
		else {
			link.setParent(((Map) serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get()));
			// add the link to the currently selected mapping
			((Map) serviceTree.getSelectionModel().getSelectedItem().getItem().itemProperty().get()).getChildren().add(link);
		}
		mappings.put(link, mapping);
		
		// if you click on a line, show the properties of the link
		mapping.getShape().addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				MainController.getInstance().showProperties(new LinkPropertyUpdater(link, mapping));
			}
		});
		mapping.setRemoveMapping(new RemoveMapping() {
			@Override
			public boolean remove(Mapping mapping) {
				mappings.remove(getLink(mappings, mapping));
				return new RemoveLinkListener(link).remove(mapping);
			}
		});
		MainController.getInstance().setChanged();
	}
	
	public static Link getLink(java.util.Map<Link, Mapping> mappings, Mapping mapping) {
		for (Link link : mappings.keySet()) {
			if (mappings.get(link).equals(mapping)) {
				return link;
			}
		}
		return null;
	}
	
	public void remove(Mapping mapping) {
		mappings.remove(getLink(mappings, mapping));
	}
	
	public static void setDefaultIndexes(ParsedPath path, TreeItem<Element<?>> parent, boolean includeLast) {
		for (TreeItem<Element<?>> child : parent.getChildren()) {
			if (child.getName().equals(path.getName())) {
				// if it's a list, set a default index
				if ((includeLast || path.getChildPath() != null) && child.itemProperty().get().getType().isList(child.itemProperty().get().getProperties())) {
					if (path.getIndex() == null) {
						path.setIndex("0");
					}
				}
				// recurse
				if (path.getChildPath() != null) {
					setDefaultIndexes(path.getChildPath(), child, includeLast);
				}
			}
		}
	}

	public MainController getController() {
		return controller;
	}

}

