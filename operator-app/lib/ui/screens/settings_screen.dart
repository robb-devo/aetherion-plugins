import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../app_version.dart';
import '../../l10n/app_localizations.dart';
import '../../state/session_scope.dart';
import '../../theme/aether_colors.dart';
import '../../updates/open_release.dart';
import '../widgets/dialogs.dart';
import '../widgets/glass_card.dart';
import '../widgets/role_chrome.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _url = TextEditingController();
  final _token = TextEditingController();
  final _githubToken = TextEditingController();
  bool _insecure = true;
  var _primed = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_primed) return;
    _primed = true;
    final session = SessionScope.of(context);
    _url.text = session.craftySettings.baseUrl.isEmpty
        ? 'https://135.181.18.162:8443/'
        : session.craftySettings.baseUrl;
    _insecure = session.craftySettings.allowInsecureTls;
  }

  @override
  void dispose() {
    _url.dispose();
    _token.dispose();
    _githubToken.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = SessionScope.of(context);
    final live = session.craftyLive;
    final pending = session.pendingUpdate;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        SectionHeader(title: l10n.settingsTitle, body: l10n.settingsBody),
        const SizedBox(height: 16),
        GlassCard(
          accent: live ? AetherColors.cyan : AetherColors.gold,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    live ? Icons.cloud_done_outlined : Icons.science_outlined,
                    color: live ? AetherColors.cyan : AetherColors.gold,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      live ? l10n.usingLive : l10n.usingMock,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              TextField(
                key: const Key('crafty-url'),
                controller: _url,
                keyboardType: TextInputType.url,
                decoration: InputDecoration(hintText: l10n.craftyUrlHint),
              ),
              const SizedBox(height: 10),
              TextField(
                key: const Key('crafty-token'),
                controller: _token,
                obscureText: true,
                decoration: InputDecoration(
                  hintText: session.craftySettings.apiToken.isEmpty
                      ? l10n.craftyTokenHint
                      : l10n.craftyTokenSet,
                ),
              ),
              Material(
                color: Colors.transparent,
                child: SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.allowInsecureTls),
                  value: _insecure,
                  onChanged: (v) => setState(() => _insecure = v),
                ),
              ),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilledButton(
                    key: const Key('crafty-save'),
                    onPressed: session.savingCrafty
                        ? null
                        : () async {
                            if (!session.canEditCrafty) {
                              showRoleRestrictedSnack(context);
                              return;
                            }
                            final err = await session.saveCraftySettings(
                              baseUrl: _url.text,
                              apiToken: _token.text,
                              allowInsecureTls: _insecure,
                            );
                            if (!context.mounted) return;
                            if (err == 'restricted') {
                              showRoleRestrictedSnack(context);
                              return;
                            }
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(l10n.savedSettings)),
                            );
                          },
                    child: Text(l10n.save),
                  ),
                  OutlinedButton(
                    key: const Key('crafty-test'),
                    onPressed: session.testCraftyConnection,
                    child: Text(l10n.testConnection),
                  ),
                ],
              ),
              if (!session.canEditCrafty) ...[
                const SizedBox(height: 10),
                Text(
                  l10n.roleRestricted,
                  style: const TextStyle(
                    color: AetherColors.mist,
                    fontSize: 12.5,
                  ),
                ),
              ],
              if (!session.craftyLive) ...[
                const SizedBox(height: 12),
                Text(
                  l10n.mockConnectBody,
                  style: const TextStyle(
                    color: AetherColors.gold,
                    fontSize: 12.5,
                    height: 1.35,
                  ),
                ),
              ],
              if (session.craftyTestMessage != null) ...[
                const SizedBox(height: 10),
                Text(
                  session.craftyTestMessage!,
                  style: TextStyle(
                    color: session.craftyTestOk == false
                        ? AetherColors.offline
                        : AetherColors.online,
                  ),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 16),
        GlassCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.currentVersion(session.appVersion),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 4),
              Text(
                l10n.buildStamp(kOperatorBuildStamp),
                style: const TextStyle(color: AetherColors.mist, fontSize: 12),
              ),
              const SizedBox(height: 6),
              Text(
                l10n.updateHowTo,
                style: const TextStyle(color: AetherColors.mist, height: 1.35),
              ),
              const SizedBox(height: 12),
              TextField(
                key: const Key('github-token'),
                controller: _githubToken,
                obscureText: true,
                decoration: InputDecoration(
                  hintText: session.githubToken.isEmpty
                      ? l10n.githubTokenHint
                      : l10n.githubTokenSet,
                ),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  OutlinedButton(
                    key: const Key('github-token-save'),
                    onPressed: session.savingGithubToken
                        ? null
                        : () async {
                            if (!session.canEditCrafty) {
                              showRoleRestrictedSnack(context);
                              return;
                            }
                            final err = await session.saveGithubToken(
                              _githubToken.text,
                            );
                            if (!context.mounted) return;
                            if (err == 'restricted') {
                              showRoleRestrictedSnack(context);
                              return;
                            }
                            _githubToken.clear();
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(l10n.savedGithubToken)),
                            );
                          },
                    child: Text(l10n.save),
                  ),
                  OutlinedButton.icon(
                    key: const Key('check-updates'),
                    onPressed:
                        session.checkingUpdates ? null : session.checkForUpdate,
                    icon: session.checkingUpdates
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.system_update_alt, size: 18),
                    label: Text(l10n.checkUpdates),
                  ),
                  if (pending != null)
                    FilledButton.icon(
                      key: const Key('open-release'),
                      onPressed: () => applyOperatorUpdate(context, pending),
                      icon: const Icon(Icons.system_update, size: 18),
                      label: Text(l10n.updateNow),
                    ),
                ],
              ),
              if (pending != null) ...[
                const SizedBox(height: 8),
                Text(
                  l10n.updateAvailable(pending.version),
                  style: const TextStyle(color: AetherColors.gold),
                ),
                Text(
                  pending.tag,
                  style: const TextStyle(
                    color: AetherColors.mist,
                    fontSize: 12,
                  ),
                ),
              ] else if (session.updateCheckFailed)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    l10n.updateCheckFailed,
                    style: const TextStyle(color: AetherColors.mist),
                  ),
                )
              else if (session.updateChecked)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    l10n.upToDate,
                    style: const TextStyle(color: AetherColors.mist),
                  ),
                ),
              const SizedBox(height: 8),
              InkWell(
                onTap: () => launchUrl(
                  Uri.parse(
                    'https://github.com/$kOperatorGithubRepo/releases',
                  ),
                  mode: LaunchMode.externalApplication,
                ),
                child: Text(
                  'github.com/$kOperatorGithubRepo/releases',
                  style: const TextStyle(
                    color: AetherColors.cyan,
                    fontSize: 12,
                    decoration: TextDecoration.underline,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
