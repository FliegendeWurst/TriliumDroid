package eu.fliegendewurst.triliumdroid.service

import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.data.Note
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.util.Locale

object DateNotes {
	private val YEAR: DateTimeFormatter = DateTimeFormatterBuilder()
		.appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
		.toFormatter()
	private val YEAR_MONTH: DateTimeFormatter = DateTimeFormatterBuilder()
			.appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
			.appendLiteral('-')
			.appendValue(ChronoField.MONTH_OF_YEAR, 2)
			.toFormatter()

	val MONTH: DateTimeFormatter = DateTimeFormatterBuilder()
		.appendValue(ChronoField.MONTH_OF_YEAR, 2, 2, SignStyle.NEVER)
		.appendLiteral(" - ")
		.appendPattern("MMMM")
		.toFormatter(Locale.ENGLISH)

	val DAY: DateTimeFormatter = DateTimeFormatterBuilder()
		.appendValue(ChronoField.DAY_OF_MONTH, 2, 2, SignStyle.NEVER)
		.appendLiteral(" - ")
		.appendPattern("EEEE")
		.toFormatter(Locale.ENGLISH)

	private fun dateFromIso(isoDate: String): LocalDate {
		val parts = isoDate.split("-")
		return LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
	}

	private var calendarRoot: Note? = null

	fun getCalendarRoot(): Note? {
		if (calendarRoot != null) {
			return calendarRoot
		}
		val calendarRoots = Cache.getNotesWithAttribute("calendarRoot", null)
		return if (calendarRoots.isNotEmpty()) {
			calendarRoot = calendarRoots[0]
			calendarRoot
		} else {
			null
		}
	}

	fun getInboxNote(): Note {
		val inboxNotes = Cache.getNotesWithAttribute("inbox", null)
		return if (inboxNotes.isNotEmpty()) {
			inboxNotes[0]
		} else {
			getTodayNote() ?: Cache.getNote("root")!!
		}
	}

	fun getTodayNote(): Note? {
		val date = DateTimeFormatter.ISO_LOCAL_DATE.format(OffsetDateTime.now())
		return getDayNote(date)
	}

	/**
	 * Format expected: YYYY-MM-DD
	 */
	fun getDayNote(isoDate: String): Note? {
		val todayNote = Cache.getNotesWithAttribute("dateNote", isoDate)
		return if (todayNote.isNotEmpty()) {
			todayNote[0]
		} else {
			// create the new date note
			val month = YEAR_MONTH.format(OffsetDateTime.now())
			val monthNote = getMonthNote(month) ?: return null
			val dayLabel = DAY.format(dateFromIso(isoDate))

			val dayNote = Cache.createChildNote(monthNote, dayLabel)

			// TODO: set dateNote attribute

			return dayNote
		}
	}

	/**
	 * Format expected: YYYY-MM-DD
	 */
	fun getWeekNote(isoDate: String): Note? {
		TODO("xxx")
	}

	/**
	 * Format expected: YYYY-MM
	 */
	fun getMonthNote(yearMonth: String): Note? {
		val monthNotes = Cache.getNotesWithAttribute("monthNote", yearMonth)
		return if (monthNotes.isNotEmpty()) {
			monthNotes[0]
		} else {
			// create the new month note
			val year = YEAR.format(OffsetDateTime.now())
			val yearNote = getYearNote(year) ?: return null
			val monthLabel = MONTH.format(dateFromIso("$yearMonth-01"))

			val monthNote = Cache.createChildNote(yearNote, monthLabel)

			// TODO: set monthNote attribute

			return monthNote
		}
	}

	fun getYearNote(year: String): Note? {
		val yearNotes = Cache.getNotesWithAttribute("yearNote", year)
		return if (yearNotes.isNotEmpty()) {
			yearNotes[0]
		} else {
			// create the new year note
			val root = getCalendarRoot() ?: return null

			val yearNote = Cache.createChildNote(root, year)

			// TODO: set yearNote attribute

			return yearNote
		}
	}

	// createSqlConsole
	// createSearchNote
}