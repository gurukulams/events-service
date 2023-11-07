package com.gurukulams.event.service;

import com.gurukulams.event.EventManager;
import com.gurukulams.event.model.Event;
import com.gurukulams.event.model.EventCategory;
import com.gurukulams.event.model.EventLearner;
import com.gurukulams.event.model.EventLocalized;
import com.gurukulams.event.store.EventCategoryStore;
import com.gurukulams.event.store.EventLearnerStore;
import com.gurukulams.event.store.EventLocalizedStore;
import com.gurukulams.event.store.EventStore;
import com.gurukulams.event.store.EventTagStore;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.hibernate.validator.internal.engine.ConstraintViolationImpl;

import static com.gurukulams.event.store.EventStore.id;
import static com.gurukulams.event.store.EventStore.title;
import static com.gurukulams.event.store.EventStore.description;
import static com.gurukulams.event.store.EventStore.eventDate;
import static com.gurukulams.event.store.EventStore.modifiedBy;

import static com.gurukulams.event.store.EventLocalizedStore.locale;
import static com.gurukulams.event.store.EventLocalizedStore.eventId;

import java.lang.annotation.ElementType;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                end as description,meeting_url,event_date,
                created_at, created_by, modified_at, modified_by
            from events c
            left join events_localized cl on c.id = cl.event_id
            where cl.locale is null
                or cl.locale = ?
            """;
    /**
     * Even Advance Creation Days.
     */
    private static final int MAX_DAYS_IN_ADVANCE = 20;
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
     * Bean Validator.
     */
    private final Validator validator;


    /**
     * Builds a new Event service.
     *
     * @param eventManager database manager.
     * @param theValidator
     */
    public EventService(final EventManager eventManager,
                        final Validator theValidator) {
        this.eventStore = eventManager.getEventStore();
        this.eventLocalizedStore
                = eventManager.getEventLocalizedStore();
        this.eventCategoryStore =
                eventManager.getEventCategoryStore();
        this.eventTagStore =
                eventManager.getEventTagStore();
        this.eventLearnerStore =
                eventManager.getEventLearnerStore();
        this.validator = theValidator;
    }

    /**
     * Create event.
     * @param categories  the categories
     * @param tags the tags
     * @param userName the username
     * @param locale   the locale
     * @param event the event
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
        event.setId(id);
        event.setCreatedAt(LocalDateTime.now());
        event.setCreatedBy(userName);
        event.setEventDate(event.getEventDate()
                .truncatedTo(ChronoUnit.SECONDS));
        this.eventStore.insert().values(event).execute();
        if (locale != null) {
            createLocalized(id, locale, event);
        }
        for (String category : categories) {
            attachCategory(id, category);
        }
        return read(userName, id, locale).get();
    }



    /**
     * Creates Localized Event.
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
        EventLocalized localized = new EventLocalized();
        localized.setEventId(eventId);
        localized.setLocale(locale.getLanguage());
        localized.setTitle(event.getTitle());
        localized.setDescription(event.getDescription());
        return this.eventLocalizedStore.insert()
                .values(localized)
                .execute();
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
        Optional<Event> optionalEvent = (locale == null)
                ? this.eventStore.select(id)
                : eventStore.select()
                .sql(READ_QUERY + " and c.id = ?")
                .param(locale(locale.getLanguage()))
                .param(locale(locale.getLanguage()))
                .param(locale(locale.getLanguage()))
                .param(id(id))
                .optional();
        if (optionalEvent.isPresent()) {
            return Optional.of(mask(userName, optionalEvent.get()));
        }
        return optionalEvent;
    }

    /**
     * Update event.
     *
     * @param id       the id
     * @param userName the username
     * @param locale   the locale
     * @param event the event
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

        event.setEventDate(event.getEventDate()
                .truncatedTo(ChronoUnit.SECONDS));

        if (locale == null) {
            updatedRows = this.eventStore.update()
                    .set(title(event.getTitle()),
                    description(event.getDescription()),
                    eventDate(event.getEventDate()),
                    modifiedBy(userName))
                    .where(id().eq(id).and()
                            .createdBy().eq(userName)).execute();
        } else {
            updatedRows = this.eventStore.update()
                    .set(modifiedBy(userName))
                    .where(id().eq(id)
                            .and()
                            .createdBy().eq(userName))
                    .execute();
            if (updatedRows != 0) {
                updatedRows = this.eventLocalizedStore.update().set(
                        title(event.getTitle()),
                        description(event.getDescription()),
                        locale(locale.getLanguage()))
                        .where(eventId().eq(id)
                        .and().locale().eq(locale.getLanguage())).execute();

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
     * List list.
     * @param categories the categories
     * @param userName the username
     * @param locale   the locale
     * @return the list
     */
    public List<Event> list(final String userName,
                               final Locale locale,
                            final List<String> categories) throws SQLException {
        EventStore.SelectStatement.SelectQuery selectQuery;
        if (locale == null) {
            selectQuery = eventStore
                    .select()
                    .sql("SELECT id,title,description,meeting_url,event_date,"
                            + "created_at,created_by,modified_at,modified_by"
                            + " FROM events WHERE "
                            + " id IN ("
                            + getCategoryFilter(categories)
                            + ") AND event_date > now()");
        } else {
            selectQuery = eventStore
                    .select()
                    .sql(READ_QUERY
                            + " and event_date > now() and c.id IN ("
                            + getCategoryFilter(categories)
                            + ")")
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()));
        }

        for (String category: categories) {
            selectQuery.param(EventCategoryStore.categoryId(category));
        }

        return selectQuery.list()
                .stream()
                .map(event ->  this.mask(userName, event))
                .toList();
    }

    /**
     * Delete boolean.
     *
     * @param userName the username
     * @param eventId       the eventId
     * @return the boolean
     */
    public boolean delete(final String userName, final UUID eventId)
            throws SQLException {

        Optional<Event> eventOptional = this.read(userName, eventId, null);
        if (eventOptional.isPresent()
        && eventOptional.get().getCreatedBy().equals(userName)) {
            this.eventLearnerStore
                    .delete(EventLearnerStore.eventId().eq(eventId))
                    .execute();
            this.eventCategoryStore
                    .delete(EventCategoryStore.eventId().eq(eventId))
                    .execute();
            this.eventTagStore
                    .delete(EventTagStore.eventId().eq(eventId))
                    .execute();
            this.eventLocalizedStore
                    .delete(eventId().eq(eventId))
                    .execute();
            return this.eventStore
                    .delete(eventId) == 1;
        } else {
            throw new IllegalArgumentException("Event not found");
        }
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
                && !eventOptional.get().getCreatedBy().equals(userName)) {
            EventLearner eventLearner = new EventLearner();
            eventLearner.setEventId(eventId);
            eventLearner.setUserHandle(userName);
            return this.eventLearnerStore
                    .insert()
                    .values(eventLearner)
                    .execute() == 1;
        } else {
            throw new IllegalArgumentException("Event not found");
        }
    }

    /**
     * Cleaning up all event.
     */
    public void delete() throws SQLException {
        this.eventLearnerStore
                .delete()
                .execute();
        this.eventCategoryStore
                .delete()
                .execute();
        this.eventTagStore
                .delete()
                .execute();
        this.eventLocalizedStore
                .delete()
                .execute();
        this.eventStore
                .delete()
                .execute();
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

        EventCategory questionCategory = new EventCategory();
        questionCategory.setEventId(id);
        questionCategory.setCategoryId(category);

        noOfRowsInserted = this.eventCategoryStore
                .insert()
                .values(questionCategory)
                .execute();

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

    private Set<ConstraintViolation<Event>>
    isValidEvent(final Event event) {
        Set<ConstraintViolation<Event>> violations =
                new HashSet<>(validator.validate(event));
        if (violations.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            // Event Can be created only till MAX_DAYS_IN_ADVANCE
            if (event.getEventDate().isBefore(now)
                    || event.getEventDate()
                    .isAfter(now.plusDays(MAX_DAYS_IN_ADVANCE))) {
                violations.add(getConstraintViolation(event,
                        "Event Can be created before "
                                + MAX_DAYS_IN_ADVANCE + " in advance"));
            } else if (!isValidURL(event.getMeetingUrl())) {
                violations.add(getConstraintViolation(event,
                        "Event url should be valid"));
            }
        }

        return violations;
    }

    /**
     * Mask Event for non owners.
     * @param userName
     * @param event
     * @return makedEvent
     */
    private Event mask(final String userName, final Event event) {
        if (!userName.equals(event.getCreatedBy())) {
            event.setMeetingUrl(null);
        }
        return event;
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

    /**
     * Isa Valid Url.
     * @param url
     * @return flag
     */
    private boolean isValidURL(final String url) {
        if (url == null) {
            return false;
        }
        // Compile the ReGex
        Pattern p = Pattern.compile("((http|https)://)(www.)?"
                + "[a-zA-Z0-9@:%._\\+~#?&//=]"
                + "{2,256}\\.[a-z]"
                + "{2,6}\\b([-a-zA-Z0-9@:%"
                + "._\\+~#?&//=]*)");

        // Find match between given string
        // and regular expression
        // using Pattern.matcher()
        Matcher m = p.matcher(url);

        // Return if the string
        // matched the ReGex
        return m.matches();
    }

}
