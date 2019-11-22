# FileSecure

Backup files from a base directory to a target directory.

To work this program needs a configuration file that will be passed as a parameter. The config file file must be in a json format. Here's an example:
```json
{
  "mappings": [
    {
      "input": "/Input",
      "output": "/Output"
    },
    {
      "input": "/Input2",
      "output": "/Output2",
      "strategy": "move",
      "depth": 2,
      "filters": [
        ".*\\.png",
        ".*\\.jpg"     
      ],
      "excludes": [
        ".*\\.ini"
      ]
    }
  ]
}
```

Each backup to do is an object in the array called "mappings". Two fields are mandatory:
* input
* output

The input folder is backed up recursively. (Maybe one day this will be configurable).

Other fields are optionals:
* strategy: define the strategy to copy files. Can be "copy", "move" or "none" (just displays in standard output). Default is none.
* depth: The number of subfolder to visit. A negative value mean an infinite number of subfolder.
* filters: regexs to apply to determine which files to keep. By default all files are kept.
* excludes: Regex to apply to determine which files to not process. Default excludes none.

When the files are moved, they are renamed with a date name "yyyy-MM-dd hh.mm.ss". This one is determined by its current name or its creation date. (Maybe one day this will be configurable).
