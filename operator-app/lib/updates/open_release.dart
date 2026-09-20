import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

import '../platform/platform_info.dart';
import 'update_checker.dart';

Future<void> openOperatorRelease(AppRelease release) async {
  final apk = release.apkUrl;
  final useApk = apk != null && !kIsWeb && !isWindowsDesktop;
  final target = useApk ? apk : release.htmlUrl;
  await launchUrl(Uri.parse(target), mode: LaunchMode.externalApplication);
}
