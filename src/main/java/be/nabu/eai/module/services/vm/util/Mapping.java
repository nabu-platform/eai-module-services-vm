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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.util.ComparableAmountListener;
import be.nabu.eai.developer.managers.util.RelativeLocationListener;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.jfx.control.line.CubicCurve;
import be.nabu.jfx.control.line.Line;
import be.nabu.jfx.control.line.QuadCurve;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Type;

public class Mapping {
	
	public enum CurveType {
		STRAIGHT,
		QUAD,
		CUBIC
	}

	/**
	 * The quad is drawn correctly and looks nice but the hit surface is not correct
	 * It slices off a piece between start, end and control point making the hit area really big
	 * This blocks access to anything underneath (like invokes)
	 * Quad can only work if we can limit hits (hovers) to hovers over the border instead of the entire body
	 */
	public static CurveType curveType = CurveType.CUBIC;
	
	private SimpleDoubleProperty sourceX = new SimpleDoubleProperty(),
			sourceY = new SimpleDoubleProperty(),
			targetX = new SimpleDoubleProperty(),
			targetY = new SimpleDoubleProperty();

	private boolean selectOnClick = false;

	private RemoveMapping removeMapping;
	
	private Shape shape;
	
	private Circle fromCircle, toCircle; 
	
	private TreeCell<Element<?>> from;
	private TreeCell<Element<?>> to;
	
	private Pane target;

	private ReadOnlyBooleanProperty lock;
	
	private Link link;

	private Label lblSource;
	private Label lblTarget;

	private HBox flwSource;

	private HBox flwTarget;

	public Mapping(Link link, Pane target, TreeCell<Element<?>> from, TreeCell<Element<?>> to, ReadOnlyBooleanProperty lock) {
		this.link = link;
		this.target = target;
		this.from = from;
		this.to = to;
		this.lock = lock;

		drawCircles();
		
		switch(curveType) {
			case STRAIGHT:
				shape = drawLine();
			break;
			case QUAD:
				shape = drawQuadCurve();
			break;
			case CUBIC:
				shape = drawCubicCurve();
			break;
		}
		setEvents(shape);
		
		target.getChildren().add(shape);
		target.getChildren().add(fromCircle);
		target.getChildren().add(toCircle);

		// we need to add the offset to the parent
		// instead of recursively determining this, first add a toscene of the tree, then substract a toscene of the target and also substract the parent offset
		RelativeLocationListener targetTransform = new RelativeLocationListener(target.localToSceneTransformProperty());
		RelativeLocationListener fromSceneTransform = new RelativeLocationListener(from.getTree().localToSceneTransformProperty());
		RelativeLocationListener toSceneTransform = new RelativeLocationListener(to.getTree().localToSceneTransformProperty());
		RelativeLocationListener fromParentTransform = new RelativeLocationListener(from.getTree().localToParentTransformProperty());
		RelativeLocationListener toParentTransform = new RelativeLocationListener(to.getTree().localToParentTransformProperty());
		
		sourceX.bind(from.rightAnchorXProperty().add(10)
			.add(fromSceneTransform.xProperty())
			.subtract(targetTransform.xProperty())
			.subtract(fromParentTransform.xProperty()));
		sourceY.bind(from.rightAnchorYProperty()
			.add(fromSceneTransform.yProperty())
			.subtract(targetTransform.yProperty())
			.subtract(fromParentTransform.yProperty()));
		targetX.bind(to.leftAnchorXProperty().subtract(10)
			.add(toSceneTransform.xProperty())
			.subtract(targetTransform.xProperty())
			.subtract(toParentTransform.xProperty()));
		targetY.bind(to.leftAnchorYProperty()
			.add(toSceneTransform.yProperty())
			.subtract(targetTransform.yProperty())
			.subtract(toParentTransform.yProperty()));
		
		
		flwSource = new HBox();
		flwTarget = new HBox();
		lblSource = new Label();
		lblTarget = new Label();
		// if we set managed to false, we don't get styling
//		flwSource.setManaged(false);
//		flwTarget.setManaged(false);
		flwSource.getChildren().add(lblSource);
		flwTarget.getChildren().add(lblTarget);
		
		flwSource.getStyleClass().add("line-label");
		flwTarget.getStyleClass().add("line-label");
		
		calculateLabels();
		
		if (shape instanceof CubicCurve) { 
			CubicCurve curve = (CubicCurve) shape;
			ChangeListener<Number> changeListener = new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
					// we add a little bit of offset because the position is the top left x,y. depending on the angle of the curve, this can vary quite a bit over the size of the label
					Point2D positionOnCurve = EAIDeveloperUtils.getPositionOnCurve(curve, 0.2f);
					flwSource.setLayoutX(positionOnCurve.getX() - 5);
					flwSource.setLayoutY(positionOnCurve.getY() - 5);
					
					positionOnCurve = EAIDeveloperUtils.getPositionOnCurve(curve, 0.8f);
					flwTarget.setLayoutX(positionOnCurve.getX() - 5);
					flwTarget.setLayoutY(positionOnCurve.getY() - 5);
				}
			};
			curve.startXProperty().addListener(changeListener);
			curve.controlX1Property().addListener(changeListener);
			curve.controlX2Property().addListener(changeListener);
			curve.endXProperty().addListener(changeListener);
			curve.startYProperty().addListener(changeListener);
			curve.controlY1Property().addListener(changeListener);
			curve.controlY2Property().addListener(changeListener);
			curve.endYProperty().addListener(changeListener);
			// trigger for initial positioning
			changeListener.changed(null, null, null);
		}
		else {
			flwSource.layoutXProperty().bind(sourceX.add(15));
			flwSource.layoutYProperty().bind(sourceY.subtract(10));
			flwTarget.layoutXProperty().bind(targetX.subtract(15));
			flwTarget.layoutYProperty().bind(targetY.subtract(10));
		}
		
		target.getChildren().addAll(flwSource, flwTarget);
	}
	
	public void calculateLabels() {
		boolean shouldShow = false;
		// the to can be null if we map to the input of an invoke service
		if (!link.isFixedValue() && link.getTo() != null) {
			shouldShow = isList(link.getFrom().replaceAll("\\[[^\\]]+\\]", ""), (ComplexType) from.getTree().rootProperty().get().itemProperty().get().getType());
			if (!shouldShow) {
				shouldShow = isList(link.getTo().replaceAll("\\[[^\\]]+\\]", ""), (ComplexType) to.getTree().rootProperty().get().itemProperty().get().getType());
			}
		}
		flwSource.getStyleClass().removeAll("line-label-many", "line-label-one");
		flwTarget.getStyleClass().removeAll("line-label-many", "line-label-one");
		if (shouldShow) {
			// if either the from or the to is a _theoretical_ list (so the return type is a list of we strip all the indexes)
			// we want to visualize the 1-* stuff
			boolean fromList = link.isFixedValue() ? false : isList(link.getFrom(), (ComplexType) from.getTree().rootProperty().get().itemProperty().get().getType());
			boolean toList = isList(link.getTo(), (ComplexType) to.getTree().rootProperty().get().itemProperty().get().getType());
			// if either is a list, we want to set both
			lblSource.setText(fromList ? "*" : "1");
			lblTarget.setText(toList ? "*" : "1");
			
			flwSource.getStyleClass().addAll("line-label-" + (fromList ? "many" : "one"));
			flwTarget.getStyleClass().addAll("line-label-" + (toList ? "many" : "one"));
			flwSource.setVisible(true);
			flwTarget.setVisible(true);
		}
		else {
			lblSource.setText(null);
			lblTarget.setText(null);
			flwSource.setVisible(false);
			flwTarget.setVisible(false);
		}
	}

	private boolean isList(String query, ComplexType context) {
		try {
			// workaround for invokes, the context is the correct output of the service, but the query has an additional locally scoped unique variable in the pipeline
			if (query.matches("^result[a-f0-9]{32}/.*")) {
				query = query.replaceFirst("^result[a-f0-9]{32}/", "");
			}
			TypeOperation operation = link.getOperation(query);
			return operation.getReturnCollectionHandler(context) != null;
		}
		catch (Exception e) {
			return false;
		}
	}
	
	public void addStyleClass(String...classes) {
		shape.getStyleClass().addAll(Arrays.asList(classes));
	}
	
	public void removeStyleClass(String...classes) {
		shape.getStyleClass().removeAll(Arrays.asList(classes));
	}
	
	private Shape drawCubicCurve() {
		CubicCurve curve = new CubicCurve();
		curve.eventSizeProperty().set(5);
		curve.setFill(null);
		curve.startXProperty().bind(sourceXProperty());
		curve.startYProperty().bind(sourceYProperty());
		curve.endXProperty().bind(targetXProperty());
		curve.endYProperty().bind(targetYProperty());
		curve.setManaged(false);
		// still not optimal
//		curve.controlX1Property().bind(targetXProperty().subtract(sourceXProperty()));
//		curve.controlY1Property().bind(sourceYProperty());
//		curve.controlX2Property().bind(targetXProperty());
//		curve.controlY2Property().bind(targetYProperty());
		
		curve.controlY1Property().bind(sourceYProperty());
		curve.controlY2Property().bind(targetYProperty());

//		curve.controlX1Property().bind(sourceXProperty().add(targetXProperty().subtract(sourceXProperty()).multiply(0.3)));
//		curve.controlX2Property().bind(sourceXProperty().add(targetXProperty().subtract(sourceXProperty()).multiply(0.7)));
		
		XDecider decider = new XDecider(sourceXProperty(), targetXProperty());
		curve.controlX1Property().bind(decider.x1);
		curve.controlX2Property().bind(decider.x2);
		return curve;
	}
	
	private static class XDecider {
		private DoubleProperty x1 = new SimpleDoubleProperty(), x2 = new SimpleDoubleProperty();
		private ReadOnlyDoubleProperty sourceX, targetX;
		
		public XDecider(ReadOnlyDoubleProperty sourceX, ReadOnlyDoubleProperty targetX) {
			this.sourceX = sourceX;
			this.targetX = targetX;
			calculate();
			sourceX.addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
					calculate();
				}
			});
			targetX.addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
					calculate();
				}
			});
		}
		
		private void calculate() {
			x1.set(Math.max(sourceX.get() + 150, sourceX.get() + ((targetX.get() - sourceX.get()) * 0.3)));
			x2.set(Math.min(targetX.get() - 150, sourceX.get() + ((targetX.get() - sourceX.get()) * 0.7)));
//			// at least some distance between them
//			if (sourceX.get() < targetX.get() - 50) {
//				x1.set(sourceX.get() + ((targetX.get() - sourceX.get()) * 0.3));
//				x2.set(sourceX.get() + ((targetX.get() - sourceX.get()) * 0.7));
//			}
//			else {
//				x1.set(sourceX.get() + 150);
//				x2.set(targetX.get() - 150);
//			}
		}
	}
	
	private Shape drawQuadCurve() {
		QuadCurve curve = new QuadCurve();
		curve.eventSizeProperty().set(5);
		curve.setFill(null);
		curve.startXProperty().bind(sourceXProperty());
		curve.startYProperty().bind(sourceYProperty());
		curve.endXProperty().bind(targetXProperty());
		curve.endYProperty().bind(targetYProperty());
		curve.setManaged(false);
//		curve.controlYProperty().bind(new ComparableAmountListener(targetYProperty(), sourceYProperty()).maxProperty());
		curve.controlYProperty().bind(targetYProperty());
		curve.controlXProperty().bind(sourceXProperty().add(targetXProperty().subtract(sourceXProperty()).divide(2d)));
		return curve;
	}
	
	private Shape drawLine() {
		Line line = new Line();
		line.eventSizeProperty().set(5);
		line.startXProperty().bind(sourceXProperty());
		line.startYProperty().bind(sourceYProperty());
		line.endXProperty().bind(targetXProperty());
		line.endYProperty().bind(targetYProperty());
		line.setManaged(false);
		return line;
	}
	
	private void setEvents(Shape shape) {		
		shape.getStyleClass().add("connectionLine");
		shape.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
//				event.consume();
				if (selectOnClick) {
					from.select();
					to.select();
				}
				else {
					from.show();
					to.show();
				}
				shape.requestFocus();
			}
		});
		shape.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getCode() == KeyCode.DELETE && lock.get()) {
					if (removeMapping != null) {
						if (removeMapping.remove(Mapping.this)) {
							remove();
							MainController.getInstance().setChanged();
						}
					}
					event.consume();
				}
			}
		});
		shape.setOnMouseEntered(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				from.getCellValue().getNode().getStyleClass().remove("lineDehover");
				to.getCellValue().getNode().getStyleClass().remove("lineDehover");
				from.getCellValue().getNode().getStyleClass().add("lineHover");
				to.getCellValue().getNode().getStyleClass().add("lineHover");
			}
		});
		shape.setOnMouseExited(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				from.getCellValue().getNode().getStyleClass().remove("lineHover");
				to.getCellValue().getNode().getStyleClass().remove("lineHover");
				from.getCellValue().getNode().getStyleClass().add("lineDehover");
				to.getCellValue().getNode().getStyleClass().add("lineDehover");
			}		
		});
	}
	
	public Shape getShape() {
		return shape;
	}
	
	private void drawCircles() {
		fromCircle = new Circle();
		fromCircle.centerXProperty().bind(sourceXProperty());
		fromCircle.centerYProperty().bind(sourceYProperty());
		fromCircle.setRadius(2);
		fromCircle.getStyleClass().add("connectionCircle");
		fromCircle.setManaged(false);
		
		toCircle = new Circle();
		toCircle.centerXProperty().bind(targetXProperty());
		toCircle.centerYProperty().bind(targetYProperty());
		toCircle.setRadius(2);
		toCircle.getStyleClass().add("connectionCircle");
		toCircle.setManaged(false);
	}
	
	public static void addToParent(Node currentNode, Node node) {
		if (currentNode.getParent() instanceof Pane)
			((Pane) currentNode.getParent()).getChildren().add(node);
		else
			addToParent(currentNode.getParent(), node);
	}
	public static Pane getPaneParent(Node currentNode) {
		if (currentNode.getParent() instanceof Pane)
			return (Pane) currentNode.getParent();
		else
			return getPaneParent(currentNode.getParent());
	}
	
	public ReadOnlyDoubleProperty sourceYProperty() {
		return sourceY;
	}
	public ReadOnlyDoubleProperty sourceXProperty() {
		return sourceX;
	}
	public ReadOnlyDoubleProperty targetYProperty() {
		return targetY;
	}
	public ReadOnlyDoubleProperty targetXProperty() {
		return targetX;
	}

	public TreeCell<Element<?>> getFrom() {
		return from;
	}
	
	public TreeCell<Element<?>> getTo() {
		return to;
	}

	public boolean isSelectOnClick() {
		return selectOnClick;
	}

	public void setSelectOnClick(boolean selectOnClick) {
		this.selectOnClick = selectOnClick;
	}
	public RemoveMapping getRemoveMapping() {
		return removeMapping;
	}
	public void setRemoveMapping(RemoveMapping removeMapping) {
		this.removeMapping = removeMapping;
	}
	public void remove() {
		target.getChildren().remove(shape);
		target.getChildren().remove(fromCircle);
		target.getChildren().remove(toCircle);
		target.getChildren().remove(flwSource);
		target.getChildren().remove(flwTarget);
	}
	
	public static interface RemoveMapping {
		public boolean remove(Mapping mapping);
	}

}
