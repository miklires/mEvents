package io.github.miklires.mevents.api;
public enum EventState { SCHEDULED, PREPARING, ACTIVE, COMPLETED, CANCELLED; public boolean terminal(){return this==COMPLETED||this==CANCELLED;} }
