
package com.gurukulams.event.service;

import com.gurukulams.event.model.Event;
import com.gurukulams.event.store.EventStore;
import com.gurukulams.event.util.TestUtil;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static com.gurukulams.event.store.EventStore.eventDate;
import static com.gurukulams.event.store.EventStore.id;
import static com.gurukulams.event.util.TestUtil.getDataSource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


class EventServiceTest {


    private static final String USERNAME_1 = "hari";
    private static final String USERNAME_2 = "hari2";
    private final EventService eventService;
    /**
     * Datasource for persistence.
     */
    private final DataSource dataSource;
    private final EventStore eventStore;
    private final List<String> categories ;
    private final List<String> tags ;

    EventServiceTest() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();
        this.dataSource = getDataSource();
        this.eventService = new EventService(this.dataSource,
                TestUtil.dataManager(), validator);
        this.eventStore = TestUtil.dataManager().getEventStore();
        categories = List.of("c1", "c2");
        tags = List.of("t1", "t2");
    }

    /**
     * Before.
     *
     * @throws IOException the io exception
     */
    @BeforeEach
    void before() throws SQLException {
        cleanUp();
    }

    /**
     * After.
     */
    @AfterEach
    void after() throws SQLException {
        cleanUp();
    }

    private void cleanUp() throws SQLException {
        eventService.delete();
    }


    @Test
    void createInvalid() throws SQLException {
        Event newevent = anEvent();

        // Past Event Days ? - Not valid

        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            eventService.create(categories, tags, USERNAME_1
                    , null, newevent.withEventDate(LocalDateTime.now().minusDays(2L)));
        });

        // Beyond 20 Days ? - Not valid

        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            eventService.create(categories, tags, USERNAME_1
                    , null, newevent.withEventDate(LocalDateTime.now().plusDays(21L)));
        });


    }

    @Test
    void create() throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1
                , null, anEvent());
        Assertions.assertTrue(eventService.read(USERNAME_1, event.id(), null).isPresent(), "Created Event");
    }

    @Test
    void createLocalized() throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1
                , Locale.GERMAN, anEvent());
        Assertions.assertTrue(eventService.read(USERNAME_1, event.id(), Locale.GERMAN).isPresent(), "Created Localized Event");
        Assertions.assertTrue(eventService.read(USERNAME_1, event.id(), null).isPresent(), "Created Event");
    }

    @Test
    void read() throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1,
                null, anEvent());
        Assertions.assertTrue(eventService.read(USERNAME_1, event.id(), null).isPresent(),
                "Created Event");
    }

    @Test
    void update() throws SQLException {

        Event updatedEvent ;

        LocalDateTime eventDate = LocalDateTime.now().plusDays(4L).truncatedTo(ChronoUnit.SECONDS);

        final Event event = eventService.create(categories,tags, USERNAME_1,
                null, anEvent()).withTitle("MyTitle2")
                .withDescription("MyDescription2")
                .withEventDate(eventDate);
        updatedEvent = eventService
                .update(event.id(), USERNAME_1, null, event);
        Assertions.assertEquals("MyTitle2", updatedEvent.title(), "Updated");

        Assertions.assertEquals("MyDescription2", updatedEvent.description(), "Updated");
        Assertions.assertEquals(eventDate, updatedEvent.eventDate(), "Updated");

        // Update With Another ID ? - Not valid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(UUID.randomUUID(), "priya", null, event);
        });
        // Update By Another User ? - Not valid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(event.id(), USERNAME_2, null, event);
        });

        // Past Event Days ? - Not valid

        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            eventService
                    .update(event.id(), USERNAME_2, null, event.withEventDate(LocalDateTime.now().minusDays(2L)));
        });

    }

    @Test
    void updateLocalized() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1,
                null, anEvent());
        Event newEvent = anEvent().withId(event.id())
        .withTitle("HansiEvent");
        Event updatedEvent = eventService
                .update(event.id(), USERNAME_1, Locale.GERMAN, newEvent);

        Assertions.assertEquals("HansiEvent", eventService.read(USERNAME_1, event.id(), Locale.GERMAN).get().title(), "Updated");
        Assertions.assertNotEquals("HansiEvent", eventService.read(USERNAME_1, event.id(), null).get().title(), "Updated");


        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(UUID.randomUUID(), USERNAME_1, null, newEvent);
        });
    }

    @Test
    void register() throws SQLException {

        final Event event = eventService.create(categories, tags, USERNAME_1, null,
                anEvent());

        // owner registering for his own event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.register(USERNAME_1, event.id());
        });

        // registering for invalid event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.register(USERNAME_2, UUID.randomUUID());
        });
        Assertions.assertFalse(eventService.isRegistered(USERNAME_2, event.id()));

        Assertions.assertTrue(eventService.register(USERNAME_2, event.id()));

        Assertions.assertTrue(eventService.isRegistered(USERNAME_2, event.id()));

        // registering again ? - Invalid
        Assertions.assertThrows(SQLException.class, () -> {
            eventService.register(USERNAME_2, event.id());
        });
    }

    @Test
    void start() throws SQLException, MalformedURLException {

        final Event newEvent = anEvent();

        final Event event = eventService.create(categories, tags, USERNAME_1, null,
                newEvent);

        // Start with Non Owner.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.start(USERNAME_2, event.id(),
                    new URL("https://github.com/techatpark"));
        });

        // Start with Null URL.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.start(USERNAME_1, event.id(),
                    null);
        });

        // Start with Invalid ID.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.start(USERNAME_1, UUID.randomUUID(),
                    new URL("https://github.com/techatpark"));
        });

        // Start Event Too Early ?.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
                    eventService.start(USERNAME_1, event.id(),
                            new URL("https://github.com/techatpark"));
                });


        eventService.update(event.id(),USERNAME_1,null,  event.withEventDate(LocalDateTime.now().plusMinutes(4)));
        Assertions.assertTrue(eventService.start(USERNAME_1, event.id(),
                new URL("https://github.com/techatpark")));

        // Double Start ?
        Assertions.assertThrows(SQLException.class, () -> {
        eventService.start(USERNAME_1, event.id(),
                new URL("https://github.com/techatpark"));
        });
    }

    @Test
    void join() throws SQLException, MalformedURLException {

        final Event event = eventService.create(categories, tags, USERNAME_1, null,
                anEvent());
        // Without Registration ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.join(USERNAME_2, event.id());
        });

        // Without Event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.join(USERNAME_2, UUID.randomUUID());
        });

        // Owner Joining Before Start
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.join(USERNAME_1, event.id());
        });

        eventStore.update()
                .set(eventDate(LocalDateTime.now().minusMinutes(4)))
                .where(id().eq(event.id()))
                .execute(this.dataSource);
        eventService.start(USERNAME_1, event.id(),
                new URL("https://github.com/techatpark"));
        // Owner Joining After Start
        Assertions.assertNotNull(eventService.join(USERNAME_1, event.id()));

        eventService.register(USERNAME_2, event.id());

        // Participant Joining
        Assertions.assertNotNull(eventService.join(USERNAME_2, event.id()));

    }

    @Test
    void delete() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1, null,
                anEvent());
        // Not a owner ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.delete(USERNAME_2, event.id());
        });

        // Not existing event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.delete(USERNAME_1, UUID.randomUUID());
        });

        eventService.delete(USERNAME_1, event.id());
        Assertions.assertFalse(eventService.read(USERNAME_1, event.id(), null).isPresent(), "Deleted Event");
    }


    @Test
    void listUserEvents() throws SQLException {
        listUserEvents(null);
        cleanUp();
        listUserEvents(Locale.GERMAN);
    }

    void listUserEvents(Locale locale) throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1, locale,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags, USERNAME_1, locale,
                newEvent);
        List<Event> events = eventService.list(USERNAME_1, locale);
        Assertions.assertEquals(2,  events.size());

        Assertions.assertEquals(0, eventService.list(USERNAME_2, locale).size());
        eventService.register(USERNAME_2, events.get(0).id());
        Assertions.assertEquals(1, eventService.list(USERNAME_2, locale).size());
        eventService.create(categories,tags, USERNAME_2, locale,
                anEvent());
        Assertions.assertEquals(2, eventService.list(USERNAME_2, locale).size());
        eventService.register(USERNAME_2, events.get(1).id());
        Assertions.assertEquals(3, eventService.list(USERNAME_2, locale).size());
    }

    @Test
    void list() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1, null,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags, USERNAME_1, null,
                newEvent);
        List<Event> listofEvents = eventService.list(USERNAME_1, null, categories);
        Assertions.assertEquals(2, listofEvents.size());


        // Check it ignores past events

        int updated = this.eventStore.update()
                .set(eventDate(LocalDateTime.now().minusDays(5L)))
                .where(id().eq(listofEvents.get(0).id()))
                .execute(this.dataSource);
        listofEvents = eventService.list(USERNAME_2, null, categories);
        Assertions.assertEquals(1, listofEvents.size());

    }

    @Test
    void listLocalized() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1, Locale.GERMAN,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags, USERNAME_1, null,
                newEvent);
        List<Event> listofEvents = eventService.list(USERNAME_1, null, categories);
        Assertions.assertEquals(2, listofEvents.size());

        listofEvents = eventService.list(USERNAME_1, Locale.GERMAN, categories);
        Assertions.assertEquals(2, listofEvents.size());

    }


    /**
     * Gets practice.
     *
     * @return the practice
     */
    Event anEvent() {
        Event event = new Event(UUID.randomUUID(),
           "HariEvent",
           "HariDescription",
           LocalDateTime.now().plusDays(2L),
        null,
        null,
        null,
        null);
        return event;
    }
}
