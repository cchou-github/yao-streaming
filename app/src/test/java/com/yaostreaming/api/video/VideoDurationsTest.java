package com.yaostreaming.api.video;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

class VideoDurationsTest {

	@ParameterizedTest
	@CsvSource({
			"0,0:00",
			"5,0:05",
			"59,0:59",
			"60,1:00",
			"91,1:31",
			"599,9:59",
			"3599,59:59",
			"3600,1:00:00",
			"3700,1:01:40",
			"86399,23:59:59"
	})
	void formatsSecondsForDisplay(int seconds, String expected) {
		assertThat(VideoDurations.format(seconds)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullSource
	void showsPlaceholderWhenDurationIsUnknown(Integer seconds) {
		assertThat(VideoDurations.format(seconds)).isEqualTo("--:--");
	}

	@ParameterizedTest
	@CsvSource({ "-1", "-90" })
	void showsPlaceholderForNonsenseValues(int seconds) {
		assertThat(VideoDurations.format(seconds)).isEqualTo("--:--");
	}

}
