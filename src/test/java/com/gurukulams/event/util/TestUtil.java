package com.gurukulams.event.util;

import com.gurukulams.event.EventManager;
import org.postgresql.ds.PGSimpleDataSource;
public class TestUtil {
    public static EventManager eventManager() {
        PGSimpleDataSource ds = new PGSimpleDataSource() ;
        ds.setURL( "jdbc:postgresql://localhost:5432/gurukulams_event" );
        ds.setUser( "tom" );
        ds.setPassword( "password" );
        return EventManager.getManager(ds);
    }

}
