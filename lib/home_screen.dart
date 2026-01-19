// File: lib/screens/home_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:io';

import 'audioplayerscreen.dart';
import 'native_recorder_bridge.dart';



class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  bool _isAccessibilityEnabled = false;
  bool _isBatteryOptimizationDisabled = false;
  bool _isRecording = false;
  List<FileSystemEntity> _recordings = [];

  // S3 Configuration
  final TextEditingController _s3UrlController = TextEditingController();
  final TextEditingController _s3TokenController = TextEditingController();
  bool _isS3Configured = false;
  bool _isUploading = false;

  // Method Channel for receiving broadcasts from native
  static const platform = MethodChannel('com.example.recorder/native');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initializeApp();
    _setupBroadcastReceiver();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _s3UrlController.dispose();
    _s3TokenController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _loadRecordings();
      _checkAccessibilityStatus();
      _checkBatteryOptimization();
      _loadS3Config();
    }
  }

  Future<void> _setupBroadcastReceiver() async {
    platform.setMethodCallHandler((call) async {
      if (call.method == 'onNewRecording') {
        final String? filePath = call.arguments as String?;
        if (filePath != null) {
          debugPrint('📥 New recording received: $filePath');
          await _loadRecordings();

          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('New recording saved: ${filePath.split('/').last}'),
                duration: const Duration(seconds: 3),
                action: SnackBarAction(
                  label: 'View',
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => AudioPlayerScreen(
                          filePath: filePath,
                          fileName: filePath.split('/').last,
                        ),
                      ),
                    );
                  },
                ),
              ),
            );
          }
        }
      }
    });
  }

  Future<void> _initializeApp() async {
    await _checkAccessibilityStatus();
    await _checkBatteryOptimization();
    await _loadRecordings();
    await _loadS3Config();
  }

  Future<void> _checkAccessibilityStatus() async {
    final enabled = await NativeRecorderBridge.isAccessibilityServiceEnabled();
    setState(() {
      _isAccessibilityEnabled = enabled;
    });

    // Show setup instructions if accessibility just got enabled
    if (enabled && !_isBatteryOptimizationDisabled && mounted) {
      // Small delay to let user see the success first
      Future.delayed(const Duration(milliseconds: 500), () {
        if (mounted) {
          _showSetupInstructions();
        }
      });
    }
  }

  Future<void> _checkBatteryOptimization() async {
    final disabled = await NativeRecorderBridge.isBatteryOptimizationDisabled();
    setState(() {
      _isBatteryOptimizationDisabled = disabled;
    });
  }

  Future<void> _loadS3Config() async {
    final config = await NativeRecorderBridge.getS3Config();
    setState(() {
      _isS3Configured = config['isConfigured'] ?? false;
      _s3UrlController.text = config['s3Url'] ?? '';
      _s3TokenController.text = config['authToken'] ?? '';
    });
  }

  Future<void> _loadRecordings() async {
    try {
      final recordingsPath = await NativeRecorderBridge.getRecordingsDirectory();

      if (recordingsPath == null) {
        debugPrint('❌ Could not get recordings directory from native');
        return;
      }

      final directory = Directory(recordingsPath);
      debugPrint('📂 Looking for recordings in: ${directory.path}');

      if (await directory.exists()) {
        final allFiles = directory.listSync();
        final files = allFiles
            .where((file) => file.path.endsWith('.m4a'))
            .toList();
        files.sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));

        setState(() {
          _recordings = files;
        });

        debugPrint('✅ Loaded ${files.length} .m4a recordings');
      }
    } catch (e) {
      debugPrint('❌ Error loading recordings: $e');
    }
  }

  Future<void> _openAccessibilitySettings() async {
    await NativeRecorderBridge.openAccessibilitySettings();

    if (mounted) {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Enable Accessibility Service'),
          content: const SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Follow these steps:\n',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                Text('1. Find "Call Recorder" in the list\n'),
                Text('2. Turn ON the service\n'),
                Text('3. Grant all permissions when asked\n'),
                Text('4. Come back to the app\n'),
                SizedBox(height: 16),
                Text(
                  '⚠️ IMPORTANT:',
                  style: TextStyle(fontWeight: FontWeight.bold, color: Colors.red),
                ),
                Text('Recording will start AUTOMATICALLY when calls begin!'),
                Text('App can be closed - recordings continue in background.'),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _checkAccessibilityStatus();
              },
              child: const Text('I\'ve Enabled It'),
            ),
          ],
        ),
      );
    }
  }

  /// 🔥 NEW: Show critical setup instructions (like Cube ACR does)
  void _showSetupInstructions() {
    showDialog(
      context: context,
      barrierDismissible: false, // Force user to read it
      builder: (context) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.warning_amber_rounded, color: Colors.orange.shade700),
            const SizedBox(width: 8),
            const Text('Important Setup'),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'For reliable call recording when app is closed, you MUST:',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              _buildSetupStep(
                '1',
                'Disable Battery Optimization',
                'Prevents Android from killing the service',
                Icons.battery_charging_full,
              ),
              const SizedBox(height: 12),
              _buildSetupStep(
                '2',
                'Lock App in Recent Apps',
                'Swipe down on app in recents → tap lock icon',
                Icons.lock,
              ),
              const SizedBox(height: 12),
              _buildSetupStep(
                '3',
                'Enable Auto-Start (OEM specific)',
                'Check your phone\'s app settings',
                Icons.play_circle_outline,
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.red.shade200),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.error_outline, color: Colors.red.shade700, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Without these steps, recording will STOP when you close the app!',
                        style: TextStyle(
                          color: Colors.red.shade700,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context);
            },
            child: const Text('I\'ll Do It Later'),
          ),
          ElevatedButton.icon(
            onPressed: () async {
              await NativeRecorderBridge.requestBatteryOptimization();
              await _checkBatteryOptimization();
              if (mounted) {
                Navigator.pop(context);
              }
            },
            icon: const Icon(Icons.battery_charging_full),
            label: const Text('Disable Battery Optimization'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.orange,
              foregroundColor: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  /// Helper to build setup step UI
  Widget _buildSetupStep(String number, String title, String description, IconData icon) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(
            color: Colors.blue.shade100,
            shape: BoxShape.circle,
          ),
          child: Center(
            child: Text(
              number,
              style: TextStyle(
                color: Colors.blue.shade700,
                fontWeight: FontWeight.bold,
                fontSize: 14,
              ),
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(icon, size: 16, color: Colors.blue.shade700),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                description,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.shade700,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _showS3ConfigDialog() async {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Configure S3 Upload'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: _s3UrlController,
                decoration: const InputDecoration(
                  labelText: 'S3 URL',
                  hintText: 'https://your-server.com/upload',
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _s3TokenController,
                decoration: const InputDecoration(
                  labelText: 'Auth Token',
                  hintText: 'Your access token',
                ),
                obscureText: true,
              ),
              const SizedBox(height: 16),
              const Text(
                'Configuration is saved permanently and survives app restarts.',
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              final url = _s3UrlController.text.trim();
              final token = _s3TokenController.text.trim();

              if (url.isNotEmpty && token.isNotEmpty) {
                await NativeRecorderBridge.configureS3(url, token);
                await _loadS3Config();

                Navigator.pop(context);
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('✅ S3 configured - Auto-upload enabled')),
                  );
                }
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  Future<void> _uploadAllToS3() async {
    if (!_isS3Configured) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please configure S3 first')),
      );
      return;
    }

    setState(() {
      _isUploading = true;
    });

    final count = await NativeRecorderBridge.uploadAllToS3();

    setState(() {
      _isUploading = false;
    });

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('✅ Uploaded $count recordings to S3')),
      );
    }
  }

  Future<void> _testRecording() async {
    if (_isRecording) {
      final savedPath = await NativeRecorderBridge.stopRecording();
      setState(() {
        _isRecording = false;
      });

      if (savedPath != null && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Recording saved: ${savedPath.split('/').last}')),
        );
        await _loadRecordings();
      }
    } else {
      // Generate test file path
      final recordingsPath = await NativeRecorderBridge.getRecordingsDirectory();
      if (recordingsPath != null) {
        final timestamp = DateTime.now().millisecondsSinceEpoch;
        final filePath = '$recordingsPath/test_recording_$timestamp.m4a';

        final success = await NativeRecorderBridge.startRecording(filePath);
        setState(() {
          _isRecording = success;
        });

        if (success && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Test recording started')),
          );
        }
      }
    }
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  String _formatDateTime(DateTime dateTime) {
    return '${dateTime.day}/${dateTime.month}/${dateTime.year} '
        '${dateTime.hour.toString().padLeft(2, '0')}:'
        '${dateTime.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Call Recorder'),
        actions: [
          // Battery optimization indicator
          if (_isAccessibilityEnabled)
            IconButton(
              icon: Icon(
                _isBatteryOptimizationDisabled
                    ? Icons.battery_charging_full
                    : Icons.battery_alert,
                color: _isBatteryOptimizationDisabled ? Colors.green : Colors.orange,
              ),
              onPressed: () {
                if (!_isBatteryOptimizationDisabled) {
                  _showSetupInstructions();
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('✅ Battery optimization already disabled'),
                      duration: Duration(seconds: 2),
                    ),
                  );
                }
              },
              tooltip: _isBatteryOptimizationDisabled
                  ? 'Battery optimization disabled'
                  : 'Setup required',
            ),
          IconButton(
            icon: Icon(
              _isS3Configured ? Icons.cloud_done : Icons.cloud_upload,
              color: _isS3Configured ? Colors.green : null,
            ),
            onPressed: _showS3ConfigDialog,
            tooltip: 'Configure S3',
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadRecordings,
            tooltip: 'Refresh recordings',
          ),
        ],
      ),
      body: Column(
        children: [
          // Status Card
          Card(
            margin: const EdgeInsets.all(16),
            elevation: 4,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildStatusRow(
                    icon: _isAccessibilityEnabled ? Icons.check_circle : Icons.error,
                    iconColor: _isAccessibilityEnabled ? Colors.green : Colors.red,
                    title: 'Background Service',
                    status: _isAccessibilityEnabled ? 'Running' : 'Stopped',
                    statusColor: _isAccessibilityEnabled ? Colors.green : Colors.red,
                  ),
                  if (!_isAccessibilityEnabled) ...[
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: _openAccessibilitySettings,
                        icon: const Icon(Icons.settings),
                        label: const Text('Enable Service'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.red,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                  ],

                  // Battery Optimization Status
                  if (_isAccessibilityEnabled) ...[
                    const Divider(height: 24),
                    _buildStatusRow(
                      icon: _isBatteryOptimizationDisabled
                          ? Icons.battery_charging_full
                          : Icons.battery_alert,
                      iconColor: _isBatteryOptimizationDisabled ? Colors.green : Colors.orange,
                      title: 'Battery Optimization',
                      status: _isBatteryOptimizationDisabled ? 'Disabled ✓' : 'Not Disabled',
                      statusColor: _isBatteryOptimizationDisabled ? Colors.green : Colors.orange,
                    ),
                  ],

                  const Divider(height: 24),
                  _buildStatusRow(
                    icon: _isS3Configured ? Icons.cloud_done : Icons.cloud_off,
                    iconColor: _isS3Configured ? Colors.blue : Colors.grey,
                    title: 'S3 Auto-Upload',
                    status: _isS3Configured ? 'Configured' : 'Not Set',
                    statusColor: _isS3Configured ? Colors.blue : Colors.grey,
                  ),

                  if (_isAccessibilityEnabled) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: _isBatteryOptimizationDisabled
                            ? Colors.green.shade50
                            : Colors.orange.shade50,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: _isBatteryOptimizationDisabled
                              ? Colors.green.shade200
                              : Colors.orange.shade200,
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            _isBatteryOptimizationDisabled
                                ? Icons.check_circle
                                : Icons.warning_amber,
                            color: _isBatteryOptimizationDisabled
                                ? Colors.green.shade700
                                : Colors.orange.shade700,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _isBatteryOptimizationDisabled
                                  ? '✅ Fully configured\n📱 Works when app closed\n☁️ Auto-uploads enabled'
                                  : '⚠️ Setup incomplete\n📱 May stop when closed\n⚙️ Tap battery icon to fix',
                              style: TextStyle(
                                color: _isBatteryOptimizationDisabled
                                    ? Colors.green.shade700
                                    : Colors.orange.shade700,
                                fontSize: 12,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),

          // Action Buttons
          if (_isAccessibilityEnabled)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                children: [
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _testRecording,
                      icon: Icon(_isRecording ? Icons.stop : Icons.mic),
                      label: Text(_isRecording ? 'Stop Test Recording' : 'Test Recording'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: _isRecording ? Colors.red : Colors.blue,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.all(16),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  if (_isS3Configured)
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: _isUploading ? null : _uploadAllToS3,
                        icon: _isUploading
                            ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                            : const Icon(Icons.cloud_upload),
                        label: Text(_isUploading ? 'Uploading...' : 'Upload All to S3'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.green,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.all(16),
                        ),
                      ),
                    ),
                ],
              ),
            ),

          const SizedBox(height: 16),

          // Recordings List Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Text(
                  'Recordings (${_recordings.length})',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const Spacer(),
                if (_recordings.isNotEmpty)
                  TextButton.icon(
                    onPressed: _loadRecordings,
                    icon: const Icon(Icons.refresh, size: 18),
                    label: const Text('Refresh'),
                  ),
              ],
            ),
          ),

          const SizedBox(height: 8),

          // Recordings List
          Expanded(
            child: _recordings.isEmpty
                ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.folder_open, size: 64, color: Colors.grey[400]),
                  const SizedBox(height: 16),
                  Text(
                    'No recordings yet',
                    style: TextStyle(color: Colors.grey[600], fontSize: 16),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _isAccessibilityEnabled
                        ? 'Calls will be recorded automatically'
                        : 'Enable service to start',
                    style: TextStyle(color: Colors.grey[500], fontSize: 12),
                  ),
                ],
              ),
            )
                : ListView.builder(
              itemCount: _recordings.length,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemBuilder: (context, index) {
                final file = File(_recordings[index].path);
                final stat = file.statSync();
                final fileName = file.path.split('/').last;

                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: Colors.blue.shade100,
                      child: Icon(Icons.phone_in_talk, color: Colors.blue.shade700),
                    ),
                    title: Text(
                      fileName,
                      style: const TextStyle(fontSize: 14),
                    ),
                    subtitle: Text(
                      '${_formatDateTime(stat.modified)} • ${_formatFileSize(stat.size)}',
                      style: const TextStyle(fontSize: 12),
                    ),
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => AudioPlayerScreen(
                            filePath: file.path,
                            fileName: fileName,
                          ),
                        ),
                      );
                    },
                    trailing: IconButton(
                      icon: const Icon(Icons.delete, color: Colors.red),
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (context) => AlertDialog(
                            title: const Text('Delete Recording'),
                            content: const Text('Are you sure?'),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(context, false),
                                child: const Text('Cancel'),
                              ),
                              TextButton(
                                onPressed: () => Navigator.pop(context, true),
                                child: const Text('Delete', style: TextStyle(color: Colors.red)),
                              ),
                            ],
                          ),
                        );

                        if (confirm == true) {
                          await file.delete();
                          await _loadRecordings();
                          if (mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Recording deleted')),
                            );
                          }
                        }
                      },
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusRow({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String status,
    required Color statusColor,
  }) {
    return Row(
      children: [
        Icon(icon, color: iconColor),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(fontSize: 16),
        ),
        const Spacer(),
        Text(
          status,
          style: TextStyle(
            color: statusColor,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }
}