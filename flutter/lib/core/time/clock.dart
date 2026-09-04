// -- Type Definitions

abstract interface class Clock {
  int nowEpochMilliseconds();
}

final class SystemClock implements Clock {
  const SystemClock();

  // -- Functions

  @override
  int nowEpochMilliseconds() => DateTime.now().millisecondsSinceEpoch;
}
