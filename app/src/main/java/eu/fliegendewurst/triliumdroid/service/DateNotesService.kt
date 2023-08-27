package eu.fliegendewurst.triliumdroid.service

import eu.fliegendewurst.triliumdroid.data.Note

object DateNotesService {
	fun getInboxNote(): Note {
		TODO("xxx")
	}

	fun getTodayNote(): Note {
		TODO("xxx")
	}

	/**
	 * Format expected: YYYY-MM-DD
	 */
	fun getDayNote(isoDate: String): Note {
		TODO("xxx")
	}

	/**
	 * Format expected: YYYY-MM-DD
	 */
	fun getWeekNote(isoDate: String): Note {
		TODO("xxx")
	}

	/**
	 * Format expected: YYYY-MM
	 */
	fun getMonthNote(yearMonth: String): Note {
		TODO("xxx")
	}

	fun getYearNote(year: String): Note {
		TODO("xxx")
	}

	// createSqlConsole
	// createSearchNote
}