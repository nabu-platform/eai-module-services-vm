package be.nabu.eai.module.services.vm.util;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import be.nabu.eai.developer.api.Component;
import be.nabu.eai.developer.api.Controller;
import be.nabu.libs.validator.api.ValidationMessage;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * For scrollpanes: on the scrollpane you need to set "fit left" and "fit right" in the "layout" tab of the editor
 * 		on the anchorpane in the scrollpane, you _need_ to set "use computed sizes" from the right click menu, or it won't detect changes
 *
 */
public class VMServiceController implements Initializable, Controller {

	private Stage stage;
	
	@FXML
	private Pane panService, panLeft, panRight, panMiddle, panInput, panOutput, panMap;
	
	@FXML
	private HBox hbxButtons, hbxButtons2, boxInterface;
	
	@FXML
	private Tab tabMap, tabInterface, tabDescription;
	
	@FXML
	private ScrollPane scrLeft, scrRight;
	
	@FXML
	private VBox vbxInterface;
	
	@FXML
	private TextField txtInterface;
	
	@FXML
	private MenuButton mnuInterfaces;
	
	@FXML
	private CheckBox chkValidateInput, chkValidateOutput;
	
	@FXML
	private TextArea txtDescription;
	
	private List<Node> content;
	private Scene scene;
	
	@Override
	public <C extends Controller, T extends Control> Component<C, T> getComponent( String name) {
		return null;
	}

	@Override
	public void setStage(Stage stage) {
		this.stage = stage;
	}

	@Override
	public void notify(ValidationMessage... messages) {
		// do nothing
	}

	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		getTabMap().getContent().addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getCode() == KeyCode.F11) {
					maximize();
				}
				else if (event.getCode() == KeyCode.SPACE && event.isControlDown()) {
					maximize();
				}
			}
		});
		panService.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (!getTabMap().disabledProperty().get()) {
					if (event.getCode() == KeyCode.F11) {
						maximize();
					}
					else if (event.getCode() == KeyCode.SPACE && event.isControlDown()) {
						maximize();
					}
				}
			}
		});
	}

	private void maximize() {
		if (content == null) {
			content = new ArrayList<Node>(getTabMap().getContent().getScene().getRoot().getChildrenUnmodifiable());
			scene = getTabMap().getContent().getScene();
			((Pane) scene.getRoot()).getChildren().clear();
			((Pane) scene.getRoot()).getChildren().add(getTabMap().getContent());
			AnchorPane.setBottomAnchor(getTabMap().getContent(), 0d);
			AnchorPane.setTopAnchor(getTabMap().getContent(), 0d);
			AnchorPane.setLeftAnchor(getTabMap().getContent(), 0d);
			AnchorPane.setRightAnchor(getTabMap().getContent(), 0d);
			((Pane) getTabMap().getContent()).prefWidthProperty().bind(scene.widthProperty());
			((Pane) getTabMap().getContent()).prefHeightProperty().bind(scene.heightProperty());
		}
		else {
			((Pane) scene.getRoot()).getChildren().clear();
			((Pane) scene.getRoot()).getChildren().addAll(content);
			content = null;
			Node content = getTabMap().getContent();
			getTabMap().setContent(new VBox());
			getTabMap().setContent(content);
		}
	}

	public Stage getStage() {
		return stage;
	}

	public HBox getHbxButtons() {
		return hbxButtons;
	}

	public HBox getHbxButtons2() {
		return hbxButtons2;
	}

	public Pane getPanService() {
		return panService;
	}

	public Pane getPanLeft() {
		return panLeft;
	}

	public Pane getPanRight() {
		return panRight;
	}

	public Pane getPanMiddle() {
		return panMiddle;
	}

	public Pane getPanInput() {
		return panInput;
	}

	public Pane getPanOutput() {
		return panOutput;
	}

	public Pane getPanMap() {
		return panMap;
	}

	public Tab getTabMap() {
		return tabMap;
	}

	public VBox getVbxInterface() {
		return vbxInterface;
	}

	public TextField getTxtInterface() {
		return txtInterface;
	}

	public CheckBox getChkValidateInput() {
		return chkValidateInput;
	}

	public void setChkValidateInput(CheckBox chkValidateInput) {
		this.chkValidateInput = chkValidateInput;
	}

	public CheckBox getChkValidateOutput() {
		return chkValidateOutput;
	}

	public void setChkValidateOutput(CheckBox chkValidateOutput) {
		this.chkValidateOutput = chkValidateOutput;
	}

	public Tab getTabInterface() {
		return tabInterface;
	}
	
	public void hideInterfaceTab() {
		getTabInterface().getTabPane().getTabs().remove(getTabInterface());
		getTabMap().getTabPane().getSelectionModel().select(getTabMap());		
	}

	public MenuButton getMnuInterfaces() {
		return mnuInterfaces;
	}

	public HBox getBoxInterface() {
		return boxInterface;
	}

	public TextArea getTxtDescription() {
		return txtDescription;
	}

	public void setTxtDescription(TextArea txtDescription) {
		this.txtDescription = txtDescription;
	}

	public Tab getTabDescription() {
		return tabDescription;
	}

	public void setTabDescription(Tab tabDescription) {
		this.tabDescription = tabDescription;
	}
}
