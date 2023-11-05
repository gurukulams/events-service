package com.gurukulams.event.service;

import com.gurukulams.event.EventManager;
import com.gurukulams.event.model.Event;
import com.gurukulams.event.model.EventCategory;
import com.gurukulams.event.model.EventLocalized;
import com.gurukulams.event.store.EventCategoryStore;
import com.gurukulams.event.store.EventLocalizedStore;
import com.gurukulams.event.store.EventStore;
import com.gurukulams.event.store.EventTagStore;

import static com.gurukulams.event.store.EventStore.id;
import static com.gurukulams.event.store.EventStore.title;
import static com.gurukulams.event.store.EventStore.description;
import static com.gurukulams.event.store.EventStore.eventDate;
import static com.gurukulams.event.store.EventStore.modifiedBy;

import static com.gurukulams.event.store.EventLocalizedStore.locale;
import static com.gurukulams.event.store.EventLocalizedStore.eventId;

import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
     * eventStore.
     */
    private final EventStore eventStore;

    /**
     * eventStore.
     */
    private final EventLocalizedStore eventLocalizedStore;

    /**
     * QuestionCategoryStore.
     */
    private final EventCategoryStore eventCategoryStore;


    /**
     * QuestionTagStore.
     */
    private final EventTagStore eventTagStore;


    /**
     * Builds a new Event service.
     *
     * @param eventManager database manager.
     */
    public EventService(final EventManager eventManager) {
        this.eventStore = eventManager.getEventStore();
        this.eventLocalizedStore
                = eventManager.getEventLocalizedStore();
        this.eventCategoryStore =
                eventManager.getEventCategoryStore();
        this.eventTagStore =
                eventManager.getEventTagStore();
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
        UUID id = UUID.randomUUID();
        event.setId(id);
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

        if (locale == null) {
            return this.eventStore.select(id);
        }

        return eventStore.select()
                .sql(READ_QUERY + " and c.id = ?")
                .param(locale(locale.getLanguage()))
                .param(locale(locale.getLanguage()))
                .param(locale(locale.getLanguage()))
                .param(id(id))
                .optional();
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
        int updatedRows;

        event.setEventDate(event.getEventDate()
                .truncatedTo(ChronoUnit.SECONDS));

        if (locale == null) {
            updatedRows = this.eventStore.update()
                    .set(title(event.getTitle()),
                    description(event.getDescription()),
                    eventDate(event.getEventDate()),
                    modifiedBy(userName))
                    .where(id().eq(id)).execute();
        } else {
            updatedRows = this.eventStore.update()
                    .set(modifiedBy(userName))
                    .where(id().eq(id)).execute();
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
                    .sql("SELECT id,title,description,event_date,"
                            + "created_at,created_by,modified_at,modified_by"
                            + " FROM events WHERE "
                            + " id IN ("
                            + getCategoryFilter(categories)
                            + ")");
        } else {
            selectQuery = eventStore
                    .select()
                    .sql(READ_QUERY
                            + " and c.id IN ("
                            + getCategoryFilter(categories)
                            + ")")
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()))
                    .param(locale(locale.getLanguage()));
        }

        for (String category: categories) {
            selectQuery.param(EventCategoryStore.categoryId(category));
        }
        return selectQuery.list();
    }

    /**
     * Delete boolean.
     *
     * @param userName the username
     * @param id       the id
     * @return the boolean
     */
    public boolean delete(final String userName, final UUID id)
            throws SQLException {
        this.eventCategoryStore
                .delete(EventCategoryStore.eventId().eq(id))
                .execute();
        this.eventTagStore
                .delete(EventTagStore.eventId().eq(id))
                .execute();
        this.eventLocalizedStore
                .delete(eventId().eq(id))
                .execute();
        return this.eventStore
                    .delete(id) == 1;
    }

    /**
     * Cleaning up all event.
     */
    public void delete() throws SQLException {
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

}
