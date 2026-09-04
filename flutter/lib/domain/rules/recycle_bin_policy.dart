// -- Constants

const recycleBinRetentionDays = 30;
const _millisecondsPerDay = Duration.millisecondsPerDay;
const _recycleBinRetentionMilliseconds =
    recycleBinRetentionDays * _millisecondsPerDay;

// -- Functions

int recycleBinExpireAt(int deletedAtEpochMilliseconds) =>
    deletedAtEpochMilliseconds + _recycleBinRetentionMilliseconds;

bool isRecycleBinEntryExpired({
  required int deletedAtEpochMilliseconds,
  required int nowEpochMilliseconds,
}) =>
    nowEpochMilliseconds >= recycleBinExpireAt(deletedAtEpochMilliseconds);

int recycleBinRemainingDays({
  required int deletedAtEpochMilliseconds,
  required int nowEpochMilliseconds,
}) {
  final remainingMilliseconds =
      recycleBinExpireAt(deletedAtEpochMilliseconds) - nowEpochMilliseconds;
  if (remainingMilliseconds <= 0) {
    return 0;
  }
  return (remainingMilliseconds + _millisecondsPerDay - 1) ~/
      _millisecondsPerDay;
}
