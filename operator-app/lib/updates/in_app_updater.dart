import 'dart:async';
import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:open_filex/open_filex.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../platform/platform_info.dart';
import 'update_checker.dart';

class UpdateProgress {
  const UpdateProgress({
    required this.fraction,
    required this.label,
  });

  /// 0..1 while downloading; null-ish sentinel via -1 for indeterminate.
  final double fraction;
  final String label;
}

/// Downloads the platform artifact from [release] and applies it locally.
///
/// - Android: downloads the APK and opens the system package installer.
/// - Windows: downloads the zip, stages it, swaps files after exit, relaunches.
class InAppUpdater {
  InAppUpdater({http.Client? httpClient}) : _http = httpClient ?? http.Client();

  final http.Client _http;

  Future<void> apply(
    AppRelease release, {
    void Function(UpdateProgress progress)? onProgress,
  }) async {
    if (kIsWeb) {
      throw StateError('In-app update is not available on web.');
    }

    final url = _artifactUrl(release);
    if (url == null) {
      throw StateError('No download URL for this platform in the release.');
    }

    onProgress?.call(
      const UpdateProgress(fraction: 0, label: 'download'),
    );
    final file = await _download(url, onProgress: onProgress);

    if (isWindowsDesktop) {
      onProgress?.call(
        const UpdateProgress(fraction: 1, label: 'install'),
      );
      await _applyWindowsZip(file);
      return;
    }

    if (Platform.isAndroid) {
      onProgress?.call(
        const UpdateProgress(fraction: 1, label: 'install'),
      );
      final result = await OpenFilex.open(
        file.path,
        type: 'application/vnd.android.package-archive',
      );
      if (result.type != ResultType.done) {
        throw StateError(result.message);
      }
      return;
    }

    throw StateError('In-app update is not supported on this platform.');
  }

  String? _artifactUrl(AppRelease release) {
    if (isWindowsDesktop) {
      final win = release.windowsUrl;
      if (win != null && win.startsWith('http')) return win;
    }
    final apk = release.apkUrl;
    if (apk != null && apk.startsWith('http')) return apk;
    return null;
  }

  Future<File> _download(
    String url, {
    void Function(UpdateProgress progress)? onProgress,
  }) async {
    final request = http.Request('GET', Uri.parse(url));
    request.headers['User-Agent'] = 'aetherion-operator-app';
    request.headers['Cache-Control'] = 'no-cache';
    final response = await _http.send(request);
    if (response.statusCode >= 400) {
      throw StateError('Download failed (${response.statusCode}).');
    }

    final total = response.contentLength ?? 0;
    final dir = await getTemporaryDirectory();
    final name = isWindowsDesktop
        ? 'operator_app_windows_update.zip'
        : 'operator_app_update.apk';
    final out = File(p.join(dir.path, name));
    if (await out.exists()) await out.delete();

    final sink = out.openWrite();
    var received = 0;
    try {
      await for (final chunk in response.stream) {
        sink.add(chunk);
        received += chunk.length;
        if (total > 0) {
          onProgress?.call(
            UpdateProgress(
              fraction: (received / total).clamp(0.0, 1.0),
              label: 'download',
            ),
          );
        } else {
          onProgress?.call(
            UpdateProgress(
              fraction: -1,
              label: 'download',
            ),
          );
        }
      }
      await sink.flush();
    } finally {
      await sink.close();
    }

    if (await out.length() < 1024) {
      throw StateError('Downloaded file looks empty or incomplete.');
    }
    return out;
  }

  Future<void> _applyWindowsZip(File zipFile) async {
    final exe = File(Platform.resolvedExecutable);
    final appDir = exe.parent;
    final staging = Directory(
      p.join(Directory.systemTemp.path, 'aetherion_operator_update_stage'),
    );
    if (await staging.exists()) {
      await staging.delete(recursive: true);
    }
    await staging.create(recursive: true);

    await extractFileToDisk(zipFile.path, staging.path);

    // If the zip wrapped a single folder, unwrap it.
    final children = staging.listSync();
    var payload = staging;
    if (children.length == 1 && children.first is Directory) {
      payload = children.first as Directory;
    }

    final batPath = p.join(
      Directory.systemTemp.path,
      'aetherion_operator_apply_update.bat',
    );
    final exeName = p.basename(exe.path);
    final bat = StringBuffer()
      ..writeln('@echo off')
      ..writeln('setlocal')
      ..writeln('timeout /t 2 /nobreak >nul')
      ..writeln('xcopy /E /Y /Q "${payload.path}\\*" "${appDir.path}\\" >nul')
      ..writeln('start "" "${p.join(appDir.path, exeName)}"')
      ..writeln('del "%~f0"');
    await File(batPath).writeAsString(bat.toString());

    await Process.start(
      'cmd.exe',
      ['/c', batPath],
      mode: ProcessStartMode.detached,
      workingDirectory: appDir.path,
    );
    // Give the helper a moment to spawn, then leave so files can be replaced.
    await Future<void>.delayed(const Duration(milliseconds: 400));
    exit(0);
  }
}
