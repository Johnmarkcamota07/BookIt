module com.group1.bookit {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.group1.bookit to javafx.fxml;
    exports com.group1.bookit;
}
