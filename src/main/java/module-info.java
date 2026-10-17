module com.group1.bookit {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires io.github.cdimascio.dotenv.java;

    opens com.group1.bookit to javafx.fxml;
    exports com.group1.bookit;
}
