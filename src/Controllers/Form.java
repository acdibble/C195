package Controllers;

import Models.Record;
import Models.Record.ValidationError;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Abstract generic class that creates base functionality common to all the forms. It defines abstract methods that must
 * be defined by subclasses to in order for the form data to be properly processed
 *
 * @param <T> a subclass of the Record model that can be updated/created by the Form subclass
 */
public abstract class Form<T extends Record> extends Base implements Initializable {
    @FXML
    protected TextField idField;
    @FXML
    private ButtonBar buttonBar;

    /**
     * the modes in which a form can operate
     */
    public enum Mode {
        Create,
        Read,
        Update
    }

    protected T record;
    protected Mode mode;
    protected boolean readOnly = true;
    protected Consumer<T> callback;
    private Stage stage;
    private final String windowTitle;

    public Form(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (record.getId() != 0) idField.setText(Long.toString(record.getId()));
        idField.setDisable(true);
        if (mode != Mode.Create) {
            if (mode == Mode.Read) {
                buttonBar.setVisible(false);
            }
            setFields();
            setTextFields();
        }
    }

    /**
     * Opens a form to create/view/update a record. the consumer lambda allows for the record to be asynchronously saved
     * and for the form to be cleaned up after the user is finished editing
     *
     * @param record   the record, an appointment or customer record
     * @param mode     new/view/update depending on the action
     * @param callback a function to be called with the record after the user clicks the save button
     */
    public void open(T record, Mode mode, Consumer<T> callback) {
        this.record = record;
        this.mode = mode;
        readOnly = mode == Mode.Read;
        this.callback = callback;
        openForm();
    }

    /**
     * a wrapper around the callback lambda that ensures idempotency by setting the lambda member to null after it is
     * called for the first time
     *
     * @param record the record that is to be saved/updated
     */
    private void callCallback(T record) {
        if (callback != null) {
            final Consumer<T> localCallback = callback;
            callback = null;
            localCallback.accept(record);
        }
    }

    /**
     * handles all the actions required for saving a record to the DB. the flow is:
     * 1. apply values from form to record
     * 2. validate that the record has valid values
     * 3. call the callback with the record
     * 4. close the form
     */
    @FXML
    private void handleSave() {
        try {
            for (Node button : buttonBar.getButtons()) {
                button.setDisable(true);
            }
            applyStringFormFieldsToRecord();
            applyOtherFieldsToRecord();
            record.validate();
            callCallback(record);
            handleClose();
        } catch (ValidationError err) {
            displayError(err);
            for (Node button : buttonBar.getButtons()) {
                button.setDisable(false);
            }
        }
    }

    /**
     * string fields are automatically applied to the record via reflection, non-string fields can be applied to the
     * record by implementing this method in subclasses
     */
    abstract protected void applyOtherFieldsToRecord();

    /**
     * called when the cancel button is clicked or any time the form must be closed
     */
    private void handleClose() {
        if (stage != null) stage.hide();
        stage = null;
    }

    /**
     * closes the window without saving the record. called when the 'x' or 'cancel' buttons are clicked
     *
     * @param event an action event from JavaFX when the button is clicked
     */
    @FXML
    private void handleClose(ActionEvent event) {
        callCallback(null);
        handleClose();
    }

    /**
     * applies the values from the record to the form so that an existing record can be updated. when creating a new
     * record, this method isn't called and the form values are left in their default state.
     */
    protected abstract void setFields();

    /**
     * Allows subclasses to define the path to their FXML files for dynamic and polymorphic forms.
     *
     * @return the resource url for the form FXML
     */
    protected abstract String getResourceURL();

    /**
     * allows for dynamic setting of the title of the form window based on the current action
     *
     * @return the title to be set for the form window
     */
    private String getWindowTitle() {
        return windowTitle;
    }

    /**
     * @return the width of the form window
     */
    protected abstract double getWidth();

    /**
     * @return the height of the form window
     */
    protected abstract double getHeight();

    /**
     * Opens a new window with the correct form for the controller
     */
    private void openForm() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(getResourceURL()), bundle);
            loader.setController(this);
            Scene scene = new Scene(loader.load(), getWidth(), getHeight());
            stage = new Stage();
            stage.setOnHidden(ev -> handleClose(null));
            stage.setScene(scene);
            stage.setTitle(getWindowTitle());
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception ex) {
            System.out.println(ex);
            ex.printStackTrace();
            handleClose(null);
        }
    }

    /**
     * uses #iterateStringFields to set values from the record to the form
     * 
     * @see Form#iterateStringFields(BiConsumer)
     */
    private void setTextFields() {
        iterateStringFields((textField, recordField) -> {
            try {
                final String data = (String) recordField.get(record);
                textField.setText(data);
                textField.setDisable(readOnly);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * uses #iterateStringFields to set values from the form to the record
     *
     * @see Form#iterateStringFields(BiConsumer)
     */
    private void applyStringFormFieldsToRecord() {
        iterateStringFields((textField, recordField) -> {
            try {
                recordField.set(record, textField.getText().trim());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * uses reflection to find all the members of a record that are strings
     *
     * @return a list of string Fields of the record
     */
    private List<Field> getStringFields() {
        final List<Field> output = new ArrayList<>();
        for (Field declaredField : record.getClass().getDeclaredFields()) {
            if (declaredField.getType() == String.class) {
                output.add(declaredField);
            }
        }
        return output;
    }

    /**
     * uses reflection to iterate over form TextFields and record members to find matching fields and returns
     * the matching pairs for further processing
     *
     * @param callback a lambda expression for processing the TextField and its matching member in the record
     *
     * @see Form#applyStringFormFieldsToRecord()
     * @see Form#setTextFields()
     */
    private void iterateStringFields(BiConsumer<TextField, Field> callback) {
        for (final Field declaredField : getStringFields()) {
            try {
                declaredField.setAccessible(true);
                final String fieldName = declaredField.getName();
                final Field textFieldField = getClass().getDeclaredField(String.format("%sField", fieldName));
                textFieldField.setAccessible(true);
                final TextField input = (TextField) textFieldField.get(this);
                callback.accept(input, declaredField);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    protected long getRecordId(Record record) {
        return Optional.ofNullable(record).map(Record::getId).orElse(0L);
    }
}
