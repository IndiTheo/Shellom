FROM ubuntu:24.04

RUN apt-get update && apt-get install -y curl git unzip ca-certificates

RUN curl https://mise.run | sh
ENV PATH="/root/.local/bin:$PATH"

COPY .mise.toml .

RUN mise install \
    && yes | mise exec -- sdkmanager --licenses || true
