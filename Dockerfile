# Jib base image: Zulu JRE + ffmpeg, rebuilt by the workflow and used as jib.from.image
ARG JAVA_VERSION
FROM azul/zulu-openjdk-alpine:${JAVA_VERSION}-jre-headless
RUN apk add --no-cache ffmpeg imagemagick
