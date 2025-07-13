# Open Integration Engine release verification toolkit

This repo houses the scripts to verify Open integration Engine release validity.

The script downloads a packaged release tarball, and clones the [`engine`](https://github.com/OpenIntegrationEngine/engine) repository
at the specified commit.

Open Integration Engine is then built locally and a verification script then verifies all jar files either by comparing the full file SHA-256 hash
or by comparing the hashes of each file in a `.jar`.



## Requirements
1. [`sdkman`](https://sdkman.io/)
2. `bash`
3. Some Java already installed

## Usage
Run `./verify.sh <commit to verify against> <release url from github>`

`./verify.sh "788a150f36a6bcd1db672e00d2e7ee609e2842d9" "https://github.com/OpenIntegrationEngine/engine/releases/download/v4.5.2/oie_unix_4_5_2.tar.gz"`