package com.gurukulams.event.util;

import com.gurukulams.event.DataManager;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

public class TestUtil {
    public static DataManager dataManager() {
        return DataManager.getManager();
    }

    public static DataSource getDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource() ;
        ds.setURL( "jdbc:postgresql://localhost:5432/gurukulams_event" );
        ds.setUser( "tom" );
        ds.setPassword( "password" );
        return ds;
    }

}
