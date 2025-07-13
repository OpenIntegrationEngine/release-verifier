#!/usr/bin/env bash
export SDKMAN_DIR="${HOME}/.sdkman"
[[ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]] && source "${SDKMAN_DIR}/bin/sdkman-init.sh"

############################################
#
# Configure the release and commit hash here
#
############################################

# example "788a150f36a6bcd1db672e00d2e7ee609e2842d9"
COMMIT_HASH=$1

# example "https://github.com/OpenIntegrationEngine/engine/releases/download/v4.5.2/oie_unix_4_5_2.tar.gz
DOWNLOAD_URL=$2

# cleanup
rm -rf engine oie temp
mkdir oie engine temp

curl -L -o temp/oie.tar.gz "$DOWNLOAD_URL"
tar xzf temp/oie.tar.gz -C .

# Clone engine repository at
git clone --depth=1 --revision="$COMMIT_HASH" git@github.com:OpenIntegrationEngine/engine.git engine

# Get correct groovy
sdk env install

pushd engine

# Get correct java and ant
sdk env install

pushd server
# Build without signing - is faster
ant clean
ant -f mirth-build.xml -DdisableSigning=true

# Drop out of dir stack
popd
popd

groovy backend.groovy
