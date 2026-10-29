package fr.rakambda.filesecure.migration

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files

/**
 * Standalone tool converting old configurations, run with
 * `java -cp FileSecure.jar fr.rakambda.filesecure.migration.MigrateConfigCommandKt old.json [other.json...] -o config.yaml`.
 */
class MigrateConfigCommand : CliktCommand(name = "migrate-config"){
	private val inputs by argument(help = "FileSecure 12 or MediaConverter configuration files, all merged into one new configuration")
		.path(mustExist = true, canBeDir = false, mustBeReadable = true)
		.multiple(required = true)
	private val output by option("-o", "--output", help = "YAML file to write the new configuration to, printed if not set")
		.path(canBeDir = false)
	private val force by option("-f", "--force", help = "Overwrite the output file if it exists")
		.flag()
	
	override fun help(context: Context): String = "Converts FileSecure 12 and MediaConverter configurations to the FileSecure 13 format."
	
	override fun run(){
		val output = output
		if(output != null && Files.exists(output) && !force){
			echo("$output already exists, use --force to overwrite it", err = true)
			throw ProgramResult(1)
		}
		
		val migrator = ConfigMigrator()
		val result = try{
			migrator.migrate(inputs.map { ConfigMigrator.Source(it.fileName.toString(), Files.newBufferedReader(it).use(ConfigMigrator.MAPPER::readTree)) })
		}
		catch(e: Exception){
			echo("Failed to migrate: ${e.message}", err = true)
			throw ProgramResult(1)
		}
		
		if(output == null){
			echo(result, trailingNewline = false)
		}
		else{
			output.toAbsolutePath().parent?.let(Files::createDirectories)
			Files.writeString(output, result)
			echo("New configuration written to $output", err = true)
		}
		migrator.warnings.forEach { echo("WARNING: $it", err = true) }
	}
}

fun main(args: Array<String>) = MigrateConfigCommand().main(args)
