package com.meetflow;

import com.meetflow.meetings.MeetingClosedEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Listens to closed meetings the way MF-22 and MF-20 will, so tests prove that the event reaches a
 * listener through the event publication table. Nothing in the app listens to it yet.
 */
public class RecordingMeetingClosedListener {

  private final List<MeetingClosedEvent> received = new CopyOnWriteArrayList<>();

  @ApplicationModuleListener
  void on(MeetingClosedEvent event) {
    received.add(event);
  }

  public List<MeetingClosedEvent> received() {
    return List.copyOf(received);
  }
}
