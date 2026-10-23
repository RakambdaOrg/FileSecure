# FileSecure

Moves, backs up and converts files through pipelines of stages.

Every file found in the input folder of a pipeline goes through its stages in order. A stage can filter the file out, change its destination (folders, name)
or plan its conversion into another format. Once all stages accepted the file, the conversion (if any) runs and the file is copied or moved to its
destination.

Version 13 is a rewrite in Kotlin merging FileSecure and [MediaConverter](https://github.com/RakambdaOrg/MediaConverter). Its configuration format is
new, see [migrating](#migrating-from-previous-versions).

## Running

```shell
java -jar FileSecure.jar -c config.yaml
```

| Flag                       | Description                                                                                                   |
|----------------------------|---------------------------------------------------------------------------------------------------------------|
| `-c`, `--config`           | **Mandatory**. Path to the configuration file.                                                               |
| `--dry`                    | Dry run: everything is logged, no file is copied, moved, converted or deleted (the `Convert` cache is still updated). |
| `-t`, `--threads`          | How many conversions run in parallel. Default 1.                                                              |
| `-w`, `--workers`          | How many files are processed in parallel (scanning, probing, copying). Default 8.                            |
| `-ft`, `--ffmpeg-threads`  | Value of ffmpeg's `-threads` option for each conversion.                                                      |
| `--ffmpeg`                 | Folder containing the `ffmpeg` executable. Uses the `PATH` if not set.                                       |
| `--ffprobe`                | Folder containing the `ffprobe` executable. Uses the `PATH` if not set.                                      |
| `--magick`                 | ImageMagick executable used for photo conversions. Default `magick`.                                         |
| `--affinity`               | Windows only. Mask of the CPU cores conversions run on, e.g. `7` for cores 1, 2 and 3.                       |
| `--progress/--no-progress` | Show progress bars. Enabled by default when running in a terminal.                                            |
| `--geonames-username`      | [geonames.org](https://www.geonames.org) account used to find the time zone of geotagged photos and videos. |

While running, these commands can be typed in the console:

* `p`: pause, conversions already running finish but nothing new starts.
* `r`: resume.
* `q`: quit, running conversions are stopped and their temporary files removed. Stopping the process (Ctrl+C, `SIGTERM`) does the same.

Conversions need [ffmpeg](https://ffmpeg.org) (video and audio) and [ImageMagick](https://imagemagick.org) (photos) to be installed.

## Configuration

The configuration is a YAML file. Unknown keys are rejected to catch typos. Files whose name ends with `.json` are read as JSON instead
(comments and trailing commas allowed), with the same keys.

```yaml
# Settings shared by all pipelines, see below
defaults:
  operation: MOVE

pipelines:
  # Back up photos from the phone, sorted by month and renamed with their date
  - name: Phone photos
    mappings:
      - input: /phone/DCIM
        output: /backup/Photos
    scan:
      skipHidden: true
      excludedFolders: ['\.thumbnails']
    stages:
      - type: NameFilter
        includes: ['.*\.jpg', '.*\.mp4']
      - type: IfOlderThan
        dayOffset: 2
      - type: FolderPerMonth
      - type: RenameWithDate
    setFileCreationDate: true
    folderActions:
      - type: DeleteIfEmpty

  # Convert videos to HEVC in place, files that can't be converted are left untouched
  - name: Videos
    mappings:
      - input: /media/Videos
        output: /media/Videos
    stages:
      - type: Convert
        processors: [VIDEO_TO_HEVC_MKV]
        cache: /data/videos.db
    temp: /data/temp
```

Regexes are best written between single quotes: backslashes are kept as is, and values starting with characters such as `[`, `*` or `{` would
otherwise be read as YAML syntax. A single quote inside is written `''`.

### Pipeline

| Key                   | Default  | Description                                                                                                                                                                           |
|-----------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                | `#index` | Name used in logs.                                                                                                                                                                    |
| `enabled`             | `true`   | Disabled pipelines are skipped.                                                                                                                                                       |
| `mappings`            |          | **Mandatory** unless `mappingTable` is set. List of `{input: ..., output: ...}` folders. A file at `input/a/b.txt` has `output/a/b.txt` as initial destination.                 |
| `mappingTable`        | none     | Many mappings sharing base folders, see [mapping table](#mapping-table).                                                                                                             |
| `scan.maxDepth`       | no limit | How many levels of sub-folders are visited, `1` only processes the files directly in `input`.                                                                                       |
| `scan.skipHidden`     | `false`  | Ignore hidden files and folders.                                                                                                                                                      |
| `scan.excludedFolders`| none     | Regexes matched against the folder path relative to `input`, using `/` as separator (e.g. `"a/b"`). Matching folders are not visited.                                                |
| `stages`              | none     | The [stages](#stages), applied in the given order.                                                                                                                                    |
| `operation`           | `NONE`   | What is done with accepted files: `COPY`, `MOVE`, `MOVE_WITH_COPY` (copy then delete, for moves across file systems) or `NONE` (only logs what would be done).                     |
| `conflict`            | `RENAME` | When the destination already exists: `RENAME` uses the first free name among `name (1).ext`, `name (2).ext`...; `SKIP` leaves the file untouched.                                  |
| `setFileCreationDate` | `false`  | Set the creation and modification dates of the destination to the date found in the file name or in its metadata.                                                                  |
| `temp`                | system   | Folder where conversions write before their result is moved to its destination.                                                                                                     |
| `folderActions`       | none     | [Actions](#folder-actions) applied to every visited input folder once all files are processed, deepest folders first.                                                              |
| `useDefaults`         | `true`   | Merge the [defaults](#defaults) into this pipeline.                                                                                                                                |

Enum values are case-insensitive. A file whose destination is itself (input and output being the same folder) is left untouched, unless it is converted.

### Mapping table

`mappingTable` writes many mappings between two base folders as a table, one line per mapping. Each entry `key: value` becomes the mapping
`{input: <input>/<key>, output: <output>/<value>}`, in the order of the entries, after the pipeline's own `mappings` (which can then be omitted).

| Key       | Description                                                                                                                                                                  |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `input`   | **Mandatory**. Base folder of the entry keys.                                                                                                                               |
| `output`  | **Mandatory**. Base folder of the entry values.                                                                                                                             |
| `entries` | **Mandatory**. Map of relative input path to relative output path. Paths can't be empty, absolute (`/a`, `D:/a`) or contain `..`, and each key can only appear once.       |
| `paths`   | Rewrites applied separately to every key and every value: the first rule whose `regex` fully matches it is applied, `replacement` can use its groups (`$1`, `${name}`). Paths matching no rule are kept. |

This moves `D:/base/in/LINE/alice_42` to `D:/base/out/Whatsapp/3/3/6/0/0/0/0/33600000001` and `D:/base/in/Whatsapp/33600000002` to
`D:/base/out/Instagram/someone`, Whatsapp folders being split by digit:

```yaml
mappingTable:
  input: D:/base/in
  output: D:/base/out
  paths:
    - regex: 'Whatsapp/(\d)(\d)(\d)(\d)(\d)(\d)(\d)(\d*)'
      replacement: 'Whatsapp/$1/$2/$3/$4/$5/$6/$7/$1$2$3$4$5$6$7$8'
  entries:
    LINE/alice_42: Whatsapp/33600000001
    Whatsapp/33600000002: Instagram/someone
```

Keys are always read as text. Values read as numbers or booleans (`123`, `1e3`, `true`) are rejected and must be quoted.

### Defaults

`defaults` holds settings written once for all pipelines. It accepts the same keys as a pipeline, except `name`, `enabled`, `mappings` and `mappingTable`, and is
merged into every pipeline:

* `stages` and `folderActions`: the default entries are placed before the pipeline's own.
* `scan`: merged key by key, the pipeline's keys win.
* Other keys (`operation`, `conflict`, `setFileCreationDate`, `temp`): the default applies when the pipeline doesn't set the key.

A pipeline with `useDefaults: false` ignores `defaults`.

```yaml
defaults:
  scan:
    skipHidden: true
  stages:
    - type: NameFilter
      excludes: ['.*\.db', '.*\.ini']
  operation: MOVE

pipelines:
  # Excludes .db and .ini files, then sorts by year, moved
  - mappings:
      - input: /in/Photos
        output: /out/Photos
    stages:
      - type: FolderPerYear
  # Excludes .db, .ini and .txt files (several NameFilter stages exclude every pattern of each), copied
  - mappings:
      - input: /in/Notes
        output: /out/Notes
    stages:
      - type: NameFilter
        excludes: ['.*\.txt']
    operation: COPY
  # Not affected by defaults: copies hidden files too, nothing excluded, operation NONE
  - mappings:
      - input: /in/Other
        output: /out/Other
    useDefaults: false
```

### Anchors and templates

YAML anchors (`&name`) and aliases (`*name`) repeat a value written once, and the merge key `<<` copies the keys of a mapping, the keys written
next to it taking precedence. Anchors are best defined under the top-level `templates` key, which FileSecure ignores and whose content is free.

```yaml
templates:
  cleanup: &cleanup
    - type: DeleteChildren
      names: [Thumbs.db]
    - type: DeleteIfEmpty
  archive: &archive
    stages:
      - type: FolderPerYear
    conflict: SKIP
    folderActions: *cleanup

pipelines:
  - <<: *archive
    mappings:
      - input: /in/Scans
        output: /archive/Scans
  # Same as above, with conflict: RENAME
  - <<: *archive
    mappings:
      - input: /in/Letters
        output: /archive/Letters
    conflict: RENAME
  - mappings:
      - input: /in/Photos
        output: /out/Photos
    folderActions: *cleanup
```

`<<` replaces whole keys: a pipeline merging `*archive` and writing its own `stages` doesn't get the template's stages. Unlike `defaults`, anchors
are a YAML feature and don't exist in JSON configurations.

### Stages

Several stages use **the date of the file**: the date found in its name (e.g. `2023-05-12 10.15.12.jpg`, `IMG_20230512_101512.jpg`), otherwise the
date it was taken from its metadata (EXIF, QuickTime, MP4, XMP), otherwise its last modification date.

| Type                    | Parameters                                                                                       | Description                                                                                                                                                                                                                  |
|-------------------------|--------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `NameFilter`            | `includes`, `excludes`: regex lists                                                              | Keeps files whose name fully matches one of the `includes` (all if empty) and none of the `excludes`.                                                                                                                       |
| `IfOlderThan`           | `dayOffset`, `minuteOffset`                                                                      | Keeps files whose date is older than the offset.                                                                                                                                                                            |
| `FolderPerYear`         |                                                                                                  | Prefixes the destination with `yyyy/` from the file date.                                                                                                                                                                   |
| `FolderPerMonth`        |                                                                                                  | Prefixes the destination with `yyyy/MM/`.                                                                                                                                                                                   |
| `FolderPerWeek`         |                                                                                                  | Prefixes the destination with `yyyy/MM/w/`, `w` being the week of the month.                                                                                                                                               |
| `FolderPerDay`          |                                                                                                  | Prefixes the destination with `yyyy/MM/dd/`.                                                                                                                                                                                |
| `FolderUniquePerDay`    |                                                                                                  | Prefixes the destination with `yyyy-MM-dd/`.                                                                                                                                                                                |
| `FolderCanonical`       | `elementCount` (mandatory)                                                                       | Prefixes the destination with one folder per character of its first path element: `going/file` with `3` becomes `g/o/i/going/file`.                                                                                       |
| `Lowercase`             |                                                                                                  | Lowercases the destination folders (not the file name).                                                                                                                                                                     |
| `NoRename`              |                                                                                                  | Restores the original file name.                                                                                                                                                                                            |
| `RenameWithDate`        | `format` (`yyyy-MM-dd HH.mm.ss`), `locale` (`en`), `zone`, `filters`                           | Renames the file with its date, keeping its extension. When `filters` (regexes) is set, only files whose original name matches one of them are renamed.                                                                   |
| `RenameWithDatePattern` | `regex`, `dateFormat`, `timeFormat` (mandatory), `outputFormat`, `locale`, `zone`               | Renames the file with a date read from its current name. `regex` must define the groups `date` and `time`, parsed with `dateFormat` and `timeFormat`. Files not matching are skipped.                                     |
| `RegexPath`             | `rules` (mandatory), `unmatched` (`SKIP`)                                                        | Rewrites the destination relative to the output folder (with `/` separators) with the first rule whose `regex` fully matches it, `replacement` can use its groups (`$1`, `${name}`). Files matching no rule are skipped, or kept unchanged with `unmatched: PASS`. See the example below. |
| `Convert`               | see below                                                                                        | Plans the conversion of media files.                                                                                                                                                                                        |

`RegexPath` can replace many similar mappings. This moves `DropOff/Bob - 123/a.jpg` to `Whatsapp/123/a.jpg` and `DropOff/bob_tw/a.jpg` to
`Twitter/bob_tw/a.jpg`, other files are left in place:

```yaml
mappings:
  - input: /data/DropOff
    output: /data
stages:
  - type: RegexPath
    rules:
      - regex: '(?:[^/]* - )?(\d+)/(.*)'
        replacement: Whatsapp/$1/$2
      - regex: '(bob_tw|carol_tw)/(.*)'
        replacement: Twitter/$1/$2
operation: MOVE
```

### Convert

The file is probed with ffprobe and converted by the first processor able to handle it. Its destination extension changes accordingly, so stages placed
after `Convert` see the new extension. The conversion only runs once all stages accepted the file and its destination is free, so with
`conflict: SKIP` already converted files are not converted again.

With `MOVE` or `MOVE_WITH_COPY` the source is sent to the trash (deleted where there is none) once the converted file is in place. When the destination
is the source itself, the source is replaced. A pipeline can contain only one `Convert` stage.

| Key          | Default                                      | Description                                                                                                                                                       |
|--------------|----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `processors` | `VIDEO_TO_HEVC_MKV`, `MP3_TO_AAC`, `TIFF_TO_JPG` | Processors tried in order.                                                                                                                                    |
| `extensions` | `mp4`, `mov`, `mkv`, `avi`, `tiff`, `mp3`, `m4a`, `m4v`, `ts`, `m2ts`, `mts`, `heic`, `jpg`, `raf` | Only files with these extensions are probed.                                                                    |
| `filters`    | none                                         | Probe filters, all must pass: `{type: LengthMoreThan, value: seconds}`, `{type: LengthLessThan, value: seconds}`.                                     |
| `unhandled`  | `SKIP`                                       | What happens to files that won't be converted: `SKIP` leaves them untouched, `PASS` lets them continue through the pipeline unchanged (e.g. to back them up too). |
| `cache`      | none                                         | H2 database file remembering files that can't be converted, so they aren't probed again on the next runs (until they are modified).                               |

| Processor           | Converts                                                    | Into                                                                            |
|---------------------|-------------------------------------------------------------|---------------------------------------------------------------------------------|
| `VIDEO_TO_HEVC_MKV` | `H264`, `VP9`, `WMV3`, `MPEG2` videos, HEVC with another tag or container | `HEVC` + `AAC` in `MKV` (HEVC sources are only remuxed)                     |
| `VIDEO_TO_HEVC_MP4` | same                                                        | `HEVC` + `AAC` in `MP4`                                                         |
| `VIDEO_TO_AV1_MKV`  | `H264`, `HEVC`, `VP9`, `WMV3`, `MPEG2` videos               | `AV1` + `OPUS` in `MKV`                                                         |
| `MP3_TO_AAC`        | `MP3` audio files                                           | `AAC` (`.m4a`)                                                                  |
| `AUDIO_TO_OPUS`     | `MP3`, `AAC` audio files                                    | `OPUS`                                                                          |
| `TIFF_TO_JPG`       | `TIFF` images                                               | `JPEG`                                                                          |
| `PHOTO_TO_AVIF`     | `heic`, `tiff`, `jpg`, `raf` files                          | `AVIF`                                                                          |
| `PHOTO_TO_JXL`      | `heic`, `tiff`, `jpg`, `raf` files                          | `JXL`                                                                           |

### Folder actions

| Type                | Parameters                                   | Description                                                                                  |
|---------------------|----------------------------------------------|----------------------------------------------------------------------------------------------|
| `DeleteIfEmpty`     | `depth` (`1`)                                | Deletes empty folders at least `depth` levels below the input folder (the input is level 0). |
| `DeleteIfOlderThan` | `dayOffset`, `minuteOffset`, `depth` (`1`)   | Same, for empty folders last modified before the offset.                                     |
| `DeleteChildren`    | `names`                                      | Deletes the given files in every folder, e.g. `["Thumbs.db", "desktop.ini"]`.                |

## Migrating from previous versions

A converter turns FileSecure 12 and MediaConverter configuration files into the new YAML format. Several files can be given, they are merged into one
configuration, one pipeline per old rule or conversion:

```shell
java -cp FileSecure.jar fr.rakambda.filesecure.migration.MigrateConfigCommandKt FileSecure.json MediaConverter.json -o config.yaml
# or from the sources
./gradlew migrateConfig --args="/absolute/path/FileSecure.json -o /absolute/path/config.yaml"
```

Without `-o` the result is printed. An existing output file is only replaced with `--force`. The result is checked against the new format before
being written, and warnings list what couldn't be converted exactly (unknown keys that were already ignored, transformer order, behaviour
changes): review them before using the new configuration, ideally with a `--dry` run first.

The tables below detail the conversion.

### FileSecure 12

Each `rule` becomes a pipeline, with the same `mappings` and `operation`.

| FileSecure 12                         | FileSecure 13                                                                      |
|---------------------------------------|------------------------------------------------------------------------------------|
| `filters`, `excludes`                 | A `NameFilter` stage with `includes`, `excludes`                                   |
| `folderExcludes`                      | `scan.excludedFolders`                                                             |
| `maxDepth`                            | `scan.maxDepth`                                                                    |
| `skipIfAlreadyExists: true`           | `conflict: SKIP`                                                                   |
| `fileTransformers`                    | `stages`, without the `Transformer` suffix (`FolderPerYearTransformer` → `FolderPerYear`), `name` becomes `type` |
| `folderTransformers`                  | `folderActions`, same renaming                                                     |
| `setFileCreationDate`                 | unchanged                                                                          |

Transformers used to run in an unspecified order (their priority was never applied). Stages run in the order they are written, list them in the order
you expect: filters first, then folders, then renaming.

### MediaConverter

Each `conversion` becomes a pipeline with a `Convert` stage.

| MediaConverter                 | FileSecure 13                                                                     |
|--------------------------------|-----------------------------------------------------------------------------------|
| `input`, `output`              | A mapping                                                                         |
| `deleteInput: true` (default)  | `operation: MOVE`                                                                 |
| `deleteInput: false`           | `operation: COPY` and `conflict: SKIP`                                            |
| `database`                     | `cache` of the `Convert` stage, existing database files can be reused             |
| `temp`                         | `temp`                                                                            |
| `excluded`                     | `scan.excludedFolders` (regexes, so escape special characters such as `.`)       |
| `processors`, `extensions`     | Same keys in the `Convert` stage                                                  |
| `filters` (`name`, `value`)    | `filters` of the `Convert` stage, `name` becomes `type`                           |
| hidden files were ignored      | `scan: {skipHidden: true}`                                                        |
| `-ft`, `--ffmpegThreads`       | `-ft`, `--ffmpeg-threads`                                                         |

### Behaviour changes

* Only regular files are processed. Previously, folders beyond `maxDepth` were copied or moved as if they were files.
* `DeleteIfEmpty` never deletes the input folder itself with its default depth (it used to, unlike `DeleteIfOlderThan`).
* Files without extension no longer abort the whole mapping when their destination already exists.
* With MediaConverter, an input file could be left in the temporary folder if its output already existed. The source is now only removed once the
  converted file is in place.
* The Windows CPU affinity was never applied by MediaConverter (a process id was used as a process handle), it now is.
* An error on one file or folder is logged and the others are still processed.
* Pipelines run one after another (MediaConverter ran its conversions in parallel), `--threads` conversions run in parallel within a pipeline.
