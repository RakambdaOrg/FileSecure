package fr.rakambda.filesecure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExitCodeTest{
	@Test
	fun success(){
		assertThat(exitCode(0, false)).isEqualTo(0)
	}

	@Test
	fun errors(){
		assertThat(exitCode(1, false)).isEqualTo(2)
		assertThat(exitCode(42, false)).isEqualTo(2)
	}

	@Test
	fun stoppedTakesPrecedenceOverErrors(){
		assertThat(exitCode(0, true)).isEqualTo(3)
		assertThat(exitCode(5, true)).isEqualTo(3)
	}
}
