# FileSecure

Backup files from a base directory to a target directory.

To work this program needs a configuration file that will be passed as a parameter. The config file file must be in a json format. Here's an example:

```json
{
  "rules": [
    {
      "mappings": [
        {
          "input": "/Input",
          "output": "/Output"
        }
      ]
    },
    {
      "operation": "move",
      "depth": 2,
      "filters": [
        ".*\\.png",
        ".*\\.jpg"
      ],
      "excludes": [
        ".*\\.ini"
      ],
      "mappings": [
        {
          "input": "/Input2",
          "output": "/Output2"
        }
      ]
    }
  ]
}
```

Each backup to do is an object in the array called "mappings". Two fields are mandatory:

* input
* output

The input folder is backed up recursively up to a certain depth.

Other fields are optionals:

* operation: define the strategy to copy files. Can be "copy", "move" or "none" (just displays in standard output). Default is none.
* depth: The number of sub-folders to visit.
* filters: Regexes to apply to determine which files to keep. By default, all files are kept.
* excludes: Regexes to apply to determine which files to not process. By default, none are excluded.
* folderExcludes: Regexes to apply to determine which folders to not process. This is a relative path from the `input` folder, separated by `/`. By default, none are excluded.
* skipIfAlreadyExists: If destination file already exists, skip it instead of generating a unique name for it.
* fileTransformers: Transformations to apply when moving the file.
    * TODO describe options
* folderTransformers: Transformations to apply to input folders after operation has been applied.
    * TODO describe options
