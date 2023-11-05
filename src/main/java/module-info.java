module gurukulams.event {
    requires java.base;
    requires java.sql;
    requires java.naming;
    requires jakarta.validation;
    requires org.hibernate.validator;
    requires com.h2database;

    opens com.gurukulams.event.service;
    opens db.upgrades;

    exports com.gurukulams.event.service;
    exports com.gurukulams.event.model;
    exports com.gurukulams.event;
}