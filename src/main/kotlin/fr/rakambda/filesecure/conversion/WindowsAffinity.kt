package fr.rakambda.filesecure.conversion

import com.sun.jna.Platform
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Restricts processes to some CPU cores, only supported on Windows.
 *
 * @param mask Bit mask of the allowed cores, e.g. 7 allows cores 1, 2 and 3.
 */
class WindowsAffinity(private val mask: Long){
	init{
		require(mask > 0) { "Affinity mask must be positive" }
		if(!Platform.isWindows()){
			log.warn { "CPU affinity is only supported on Windows, it will be ignored" }
		}
	}

	fun apply(process: Process){
		if(!Platform.isWindows()){
			return
		}
		log.debug { "Setting affinity $mask to pid ${process.pid()}" }
		val kernel = Kernel32.INSTANCE
		val handle = kernel.OpenProcess(WinNT.PROCESS_SET_INFORMATION or WinNT.PROCESS_QUERY_INFORMATION, false, process.pid().toInt())
		if(handle == null){
			log.warn { "Failed to open process ${process.pid()} to set its affinity, error ${kernel.GetLastError()}" }
			return
		}
		try{
			if(!kernel.SetProcessAffinityMask(handle, BaseTSD.ULONG_PTR(mask))){
				log.warn { "Failed to set affinity of process ${process.pid()}, error ${kernel.GetLastError()}" }
			}
		}
		finally{
			kernel.CloseHandle(handle)
		}
	}
}
