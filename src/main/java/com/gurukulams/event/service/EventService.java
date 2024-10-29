package com.gurukulams.event.service;

import com.gurukulams.event.DataManager;
import com.gurukulams.event.model.Event;
import com.gurukulams.event.model.EventCategory;
import com.gurukulams.event.model.EventLearner;
import com.gurukulams.event.model.EventLocalized;
import com.gurukulams.event.model.EventMeeting;
import com.gurukulams.event.store.EventCategoryStore;
import com.gurukulams.event.store.EventLearnerStore;
import com.gurukulams.event.store.EventLocalizedStore;
import com.gurukulams.event.store.EventMeetingStore;
import com.gurukulams.event.store.EventStore;
import com.gurukulams.event.store.EventTagStore;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.hibernate.validator.internal.engine.ConstraintViolationImpl;

import javax.sql.DataSource;

import static com.gurukulams.event.store.EventLocalizedStore.locale;
import static com.gurukulams.event.store.EventLocalizedStore.eventId;
import static com.gurukulams.event.store.EventStore.id;
import static com.gurukulams.event.store.EventStore.createdBy;
import static com.gurukulams.event.store.EventStore.eventDate;
import static com.gurukulams.event.store.EventStore.description;
import static com.gurukulams.event.store.EventStore.title;
import static com.gurukulams.event.store.EventStore.modifiedBy;

import java.lang.annotation.ElementType;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The type Event service.
 */
public class EventService {

    /**
     * Locale Specific Read Query.
     */
    private static final String READ_QUERY = """
            select distinct c.id,
                case when cl.locale = ?
                    then cl.title
                    else c.title
                end as title,
                case when cl.locale = ?
                    then cl.description
                    else c.description
                end as description,event_date,
                created_at, created_by, modified_at, modified_by
            from events c
            left join events_localized cl on c.id = cl.event_id
            where cl.locale is null
                or cl.locale = ?
            """;

    /**
     * Locale Specific List Query.
     */
    private static final String LIST_QUERY = READ_QUERY
                                                + " and event_date > now()";

    /**
     * Even Advance Creation Days.
     */
    private static final int MAX_DAYS_IN_ADVANCE = 20;

    /**
     * Even Advance Creation Days.
     */
    private static final int MAX_MINUTES_IN_ADVANCE_TO_START = 10;
    /**
     * GET REGISTRED USERS.
     */
    private static final String LEARNER_WHERE_USER_HANDLE
            = "select event_id from events_learner where user_handle = ?";
    /**
     * Datasource for persistence.
     */
    private final DataSource dataSource;
    /**
     * eventStore.
     */
    private final EventStore eventStore;

    /**
     * eventStore.
     */
    private final EventLocalizedStore eventLocalizedStore;

    /**
     * Event Category Store.
     */
    private final EventCategoryStore eventCategoryStore;


    /**
     * Event Tag Store.
     */
    private final EventTagStore eventTagStore;


    /**
     * Event User Store.
     */
    private final EventLearnerStore eventLearnerStore;

    /**
     * Event Meeting Store.
     */
    private final EventMeetingStore eventMeetingStore;

    /**
     * Bean Validator.
     */
    private final Validator validator;


    /**
     * Builds a new Event service.
     * @param theDataSource
     * @param dataManager      database manager.
     * @param theValidator
     */
    public EventService(final DataSource theDataSource,
                        final DataManager dataManager,
                        final Validator theValidator) {
        this.dataSource = theDataSource;
        this.eventStore = dataManager.getEventStore();
        this.eventLocalizedStore
                = dataManager.getEventLocalizedStore();
        this.eventCategoryStore =
                dataManager.getEventCategoryStore();
        this.eventTagStore =
                dataManager.getEventTagStore();
        this.eventLearnerStore =
                dataManager.getEventLearnerStore();
        this.eventMeetingStore =
                dataManager.getEventMeetingStore();
        this.validator = theValidator;
    }

    /**
     * Create event.
     *
     * @param categories the categories
     * @param tags       the tags
     * @param userName   the username
     * @param locale     the locale
     * @param event      the event
     * @return the event
     */
    public Event create(final List<String> categories,
                        final List<String> tags,
                        final String userName,
                        final Locale locale,
                        final Event event)
            throws SQLException {

        Set<ConstraintViolation<Event>> violations =
                isValidEvent(event);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        UUID id = UUID.randomUUID();
      final Event toBeCreated = event.withId(id)
        .withCreatedAt(LocalDateTime.now())
        .withCreatedBy(userName)
        .withEventDate(event.eventDate()
                .truncatedTo(ChronoUnit.SECONDS));
        this.eventStore.insert().values(toBeCreated).execute(this.dataSource);
        if (locale != null) {
            createLocalized(id, locale, toBeCreated);
        }
        for (String category : categories) {
            attachCategory(id, category);
        }
        return read(userName, id, locale).get();
    }


    /**
     * Creates Localized Event.
     *
     * @param eventId
     * @param event
     * @param locale
     * @return localized
     * @throws SQLException
     */
    private int createLocalized(final UUID eventId,
                                final Locale locale,
                                final Event event)
            throws SQLException {
        EventLocalized localized = new EventLocalized(eventId,
                locale.getLanguage(),
                event.title(),
                event.description());
        return this.eventLocalizedStore.insert()
                .values(localized)
                .execute(this.dataSource);
    }

    /**
     * Read optional.
     *
     * @param userName the username
     * @param id       the id
     * @param locale   the locale
     * @return the optional
     */
    public Optional<Event> read(final String userName,
                                final UUID id,
                                final Locale locale)
            throws SQLException {
        return (locale == null)
                ? this.eventStore.select(this.dataSource, id)
                : eventStore.select()
                .sql(READ_QUERY + " and c.id = ?")
                .param(locale(locale.getLanguage()))
                .param(locale(locale.getLanguage()))
                .param(locale(locale.getLanguage()))
                .param(id(id))
                .optional(this.dataSource);
    }

    /**
     * Update event.
     *
     * @param id       the id
     * @param userName the username
     * @param locale   the locale
     * @param event    the event
     * @return the event
     */
    public Event update(final UUID id,
                        final String userName,
                        final Locale locale,
                        final Event event) throws SQLException {

        Set<ConstraintViolation<Event>> violations =
                isValidEvent(event);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        int updatedRows;

        event.withEventDate(event.eventDate()
                .truncatedTo(ChronoUnit.SECONDS));

        if (locale == null) {
            updatedRows = this.eventStore.update()
                    .set(title(event.title()),
                            description(event.description()),
                            eventDate(event.eventDate()),
                            modifiedBy(userName))
                    .where(id().eq(id).and()
                            .createdBy().eq(userName)).execute(this.dataSource);
        } else {
            updatedRows = this.eventStore.update()
                    .set(modifiedBy(userName))
                    .where(id().eq(id)
                            .and()
                            .createdBy().eq(userName))
                    .execute(this.dataSource);
            if (updatedRows != 0) {
                updatedRows = this.eventLocalizedStore.update().set(
                                title(event.title()),
                                description(event.description()),
                                locale(locale.getLanguage()))
                        .where(eventId().eq(id)
                                .and().locale().eq(locale.getLanguage()))
                        .execute(this.dataSource);

                if (updatedRows == 0) {
                    updatedRows = createLocalized(id, locale, event);
                }
            }
        }

        if (updatedRows == 0) {
            throw new IllegalArgumentException("Event not found");
        }

        return read(userName, id, locale).get();
    }

    /**
     * Lists EVents of an User.
     * @param userName
     * @param locale
     * @return the list
     */
    public List<Event> list(final String userName,
                            final Locale locale) throws SQLException {
        EventStore.SelectStatement.SelectQuery selectQuery;

        if (locale == null) {
            selectQuery = eventStore
                .select()
                .sql("SELECT id,title,description,event_date,"
                    + "created_at,created_by,modified_at,modified_by"
                    + " FROM events WHERE event_date > now()"
                    + " AND ( created_by = ? OR id IN ("
                    + LEARNER_WHERE_USER_HANDLE
                    + "))")
                .param(createdBy(userName))
                .param(createdBy(userName));
        } else {
            selectQuery = eventStore
                    .select()
                    .sql(LIST_QUERY
                            + " AND ( c.created_by = ? OR c.id IN ("
                            + LEARNER_WHERE_USER_HANDLE
                            + "))")
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()))
                    .param(createdBy(userName))
                    .param(createdBy(userName));
        }

        return selectQuery.list(this.dataSource);
    }

    /**
     * List events for categories.
     *
     * @param categories the categories
     * @param userName   the username
     * @param locale     the locale
     * @return the list
     */
    public List<Event> list(final String userName,
                            final Locale locale,
                            final List<String> categories) throws SQLException {
        EventStore.SelectStatement.SelectQuery selectQuery;
        if (locale == null) {
            selectQuery = eventStore
                    .select()
                    .sql("SELECT id,title,description,event_date,"
                            + "created_at,created_by,modified_at,modified_by"
                            + " FROM events WHERE event_date > now()"
                            + " AND id IN ("
                            + getCategoryFilter(categories)
                            + ")");
        } else {
            selectQuery = eventStore
                    .select()
                    .sql(LIST_QUERY
                            + " and c.id IN ("
                            + getCategoryFilter(categories)
                            + ")")
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()));
        }

        for (String category : categories) {
            selectQuery.param(EventCategoryStore.categoryId(category));
        }

        return selectQuery.list(this.dataSource);
    }

    /**
     * Delete boolean.
     *
     * @param userName the username
     * @param eventId  the eventId
     * @return the boolean
     */
    public boolean delete(final String userName, final UUID eventId)
            throws SQLException {

        Optional<Event> eventOptional = this.read(userName, eventId, null);
        if (eventOptional.isPresent()
                && eventOptional.get().createdBy().equals(userName)) {
            this.eventMeetingStore
                    .delete(EventMeetingStore.eventId().eq(eventId))
                    .execute(this.dataSource);
            this.eventLearnerStore
                    .delete(EventLearnerStore.eventId().eq(eventId))
                    .execute(this.dataSource);
            this.eventCategoryStore
                    .delete(EventCategoryStore.eventId().eq(eventId))
                    .execute(this.dataSource);
            this.eventTagStore
                    .delete(EventTagStore.eventId().eq(eventId))
                    .execute(this.dataSource);
            this.eventLocalizedStore
                    .delete(eventId().eq(eventId))
                    .execute(this.dataSource);
            return this.eventStore
                    .delete(this.dataSource, eventId) == 1;
        } else {
            throw new IllegalArgumentException("Event not found");
        }
    }

    /**
     * Start for an Event.
     *
     * @param userName the username
     * @param eventId       the eventId
     * @param url
     * @return the boolean
     */
    public boolean start(final String userName,
                         final UUID eventId,
                         final URL url)
            throws SQLException {
        Optional<Event> eventOptional = this.read(userName, eventId, null);
        if (url != null
            && eventOptional.isPresent()
                && eventOptional.get().createdBy().equals(userName)) {
            LocalDateTime eventDateTime = eventOptional.get().eventDate();
            LocalDateTime start = LocalDateTime.now()
                    .minusMinutes(MAX_MINUTES_IN_ADVANCE_TO_START);
            LocalDateTime thresold = LocalDateTime.now()
                    .plusMinutes(MAX_MINUTES_IN_ADVANCE_TO_START);

            if (eventDateTime
                    .isAfter(start) && eventDateTime.isBefore(thresold)) {
                EventMeeting meeting = new EventMeeting(eventId,
                        url.toString());
                return this.eventMeetingStore
                        .insert()
                        .values(meeting)
                        .execute(this.dataSource) == 1;
            } else {
                throw new IllegalArgumentException("Event not ready to start");
            }
        } else {
            throw new IllegalArgumentException("Event not found");
        }
    }

    /**
     * is the Registered for the given event.
     *
     * @param userName the username
     * @param eventId  the eventId
     * @return the boolean
     */
    public boolean isRegistered(final String userName,
                                final UUID eventId)
            throws SQLException {
        return this.eventLearnerStore.exists(this.dataSource,
                eventId, userName);
    }

    /**
     * Register for an Event..
     *
     * @param userName the username
     * @param eventId       the eventId
     * @return the boolean
     */
    public boolean register(final String userName, final UUID eventId)
            throws SQLException {
        Optional<Event> eventOptional = this.read(userName, eventId, null);
        if (eventOptional.isPresent()
                && !eventOptional.get().createdBy().equals(userName)) {
            EventLearner eventLearner = new EventLearner(eventId,
                    userName);
            return this.eventLearnerStore
                    .insert()
                    .values(eventLearner)
                    .execute(this.dataSource) == 1;
        } else {
            throw new IllegalArgumentException("Event not found");
        }
    }

    /**
     * join an Event (only if owner or registered user).
     *
     * @param userName the username
     * @param eventId       the eventId
     * @return the boolean
     */
    public String join(final String userName, final UUID eventId)
            throws SQLException {
        Optional<Event> eventOptional
                = this.read(userName, eventId, null);
        if (eventOptional.isPresent()) {
            Optional<EventMeeting> meeting
                    = this.eventMeetingStore.select(this.dataSource, eventId);
            if (meeting.isPresent()) {
                if (eventOptional.get().createdBy().equals(userName)
                        || isRegistered(userName, eventId)) {
                    return meeting.get().meetingUrl();
                }
            }
        }
        throw new IllegalArgumentException("Event not found");
    }

    /**
     * Cleaning up all event.
     */
    public void delete() throws SQLException {
        this.eventMeetingStore
                .delete()
                .execute(this.dataSource);
        this.eventLearnerStore
                .delete()
                .execute(this.dataSource);
        this.eventCategoryStore
                .delete()
                .execute(this.dataSource);
        this.eventTagStore
                .delete()
                .execute(this.dataSource);
        this.eventLocalizedStore
                .delete()
                .execute(this.dataSource);
        this.eventStore
                .delete()
                .execute(this.dataSource);
    }

    /**
     * Attach category to an Event.
     * @param id
     * @param category
     * @return flag
     */
    private boolean attachCategory(final UUID id,
                                final String category)
            throws SQLException {
        int noOfRowsInserted = 0;

        EventCategory questionCategory = new EventCategory(id,
                category);
        noOfRowsInserted = this.eventCategoryStore
                .insert()
                .values(questionCategory)
                .execute(this.dataSource);

        return noOfRowsInserted == 1;
    }

    /**
     * Get Category Filter.
     * @param category
     * @return filter
     */
    private String getCategoryFilter(final List<String> category) {
        return "select event_id from "
                + "events_category where category_id in ("
                + category.stream().map(tag -> "?")
                .collect(Collectors.joining(","))
                + ") "
                + "group by event_id "
                + "having count(distinct category_id) = "
                + category.size();
    }

    private Set<ConstraintViolation<Event>> isValidEvent(final Event event) {
        Set<ConstraintViolation<Event>> violations =
                new HashSet<>(validator.validate(event));
        if (violations.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            // Event Can be created only till MAX_DAYS_IN_ADVANCE
            if (event.eventDate().isBefore(now)
                    || event.eventDate()
                    .isAfter(now.plusDays(MAX_DAYS_IN_ADVANCE))) {
                violations.add(getConstraintViolation(event,
                        "Event Can be created before "
                                + MAX_DAYS_IN_ADVANCE + " in advance"));
            }
        }

        return violations;
    }

    private static ConstraintViolation<Event> getConstraintViolation(
            final Event event,
            final String message) {
        final String messageTemplate = null;
        final Class<Event> rootBeanClass
                = Event.class;
        final Object leafBeanInstance = null;
        final Object cValue = null;
        final Path propertyPath = null;
        final ConstraintDescriptor<?> constraintDescriptor = null;
        final ElementType elementType = null;
        final Map<String, Object> messageParameters = new HashMap<>();
        final Map<String, Object> expressionVariables = new HashMap<>();
        return ConstraintViolationImpl.forBeanValidation(
                messageTemplate, messageParameters,
                expressionVariables,
                message,
                rootBeanClass,
                event, leafBeanInstance,
                cValue, propertyPath,
                constraintDescriptor, elementType);
    }

}
