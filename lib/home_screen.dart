import 'package:flutter/material.dart';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

import 'audioplayerscreen.dart';
import 'native_recorder_bridge.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _isAccessibilityEnabled = false;
  List<Map<String, dynamic>> _recordings = [];
  bool _isLoading = true;
  bool _setupComplete = false;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    await _requestPermissions();
    await _checkAccessibility();
    await _loadRecordings();
    _checkSetupComplete();
    if (mounted) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _checkSetupComplete() {
    // Check if accessibility is enabled
    _setupComplete = _isAccessibilityEnabled;
  }

  Future<void> _requestPermissions() async {
    try {
      await [
        Permission.microphone,
        Permission.phone,
        Permission.notification,
        Permission.storage,
      ].request();
    } catch (e) {
      debugPrint('Permission error: $e');
    }
  }

  Future<void> _checkAccessibility() async {
    try {
      final enabled = await NativeRecorderBridge.isAccessibilityServiceEnabled();
      if (mounted) {
        setState(() {
          _isAccessibilityEnabled = enabled;
        });
      }
    } catch (e) {
      debugPrint('Accessibility check error: $e');
    }
  }

  Future<void> _loadRecordings() async {
    try {
      final directory = Directory('/data/data/com.example.recorder/files/CallRecordings');

      if (await directory.exists()) {
        final entities = await directory.list().toList();
        final files = entities.where((file) => file.path.endsWith('.m4a')).toList();
        
        final recordings = <Map<String, dynamic>>[];
        for (final file in files) {
          try {
            final fileStat = await File(file.path).stat();
            recordings.add({
              'path': file.path,
              'name': file.path.split('/').last,
              'modified': fileStat.modified,
              'size': fileStat.size,
            });
          } catch (e) {
            debugPrint('Error reading file: $e');
          }
        }

        recordings.sort((a, b) => 
            (b['modified'] as DateTime).compareTo(a['modified'] as DateTime));

        if (mounted) {
          setState(() {
            _recordings = recordings;
          });
        }
      }
    } catch (e) {
      debugPrint('Error loading recordings: $e');
      try {
        final appDir = await getApplicationDocumentsDirectory();
        final altDirectory = Directory('${appDir.path}/CallRecordings');
        if (await altDirectory.exists()) {
          final entities = await altDirectory.list().toList();
          final files = entities.where((file) => file.path.endsWith('.m4a')).toList();
          
          final recordings = <Map<String, dynamic>>[];
          for (final file in files) {
            try {
              final fileStat = await File(file.path).stat();
              recordings.add({
                'path': file.path,
                'name': file.path.split('/').last,
                'modified': fileStat.modified,
                'size': fileStat.size,
              });
            } catch (e) {
              debugPrint('Error reading file: $e');
            }
          }

          recordings.sort((a, b) => 
              (b['modified'] as DateTime).compareTo(a['modified'] as DateTime));

          if (mounted) {
            setState(() {
              _recordings = recordings;
            });
          }
        }
      } catch (e2) {
        debugPrint('Alternative path also failed: $e2');
      }
    }
  }

  Future<void> _openAccessibilitySettings() async {
    await NativeRecorderBridge.openAccessibilitySettings();
    if (mounted) {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('✅ Setup Instructions'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'STEP 1: Enable Accessibility',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const SizedBox(height: 8),
                const Text(
                  '1. Find "Company Call Recorder"\n'
                  '2. Turn ON the service\n'
                  '3. Allow restricted settings if asked\n',
                ),
                const SizedBox(height: 16),
                const Text(
                  'STEP 2: Disable Battery Optimization',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const SizedBox(height: 8),
                const Text(
                  '1. Go to Settings > Apps > Call Recorder\n'
                  '2. Battery > Unrestricted\n'
                  '3. This keeps recording active when app is closed\n',
                ),
                const SizedBox(height: 16),
                const Text(
                  'STEP 3: Enable Autostart (Xiaomi/MIUI)',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const SizedBox(height: 8),
                const Text(
                  '1. Security > Autostart\n'
                  '2. Find "Call Recorder"\n'
                  '3. Turn ON\n',
                ),
                const SizedBox(height: 16),
                const Text(
                  '📱 After setup, calls will record automatically even when app is closed!',
                  style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _checkAccessibility();
              },
              child: const Text('Done'),
            ),
          ],
        ),
      );
    }
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(0)} KB';
    }
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  String _formatDateTime(DateTime dt) {
    return '${dt.day}/${dt.month}/${dt.year} '
        '${dt.hour.toString().padLeft(2, '0')}:'
        '${dt.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('🎙️ Call Recorder'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () async {
              setState(() => _isLoading = true);
              await _loadRecordings();
              await _checkAccessibility();
              setState(() => _isLoading = false);
            },
          ),
          IconButton(
            icon: const Icon(Icons.info_outline),
            onPressed: () {
              showDialog(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('ℹ️ Important Info'),
                  content: const SingleChildScrollView(
                    child: Text(
                      '📱 HOW IT WORKS:\n\n'
                      '1. Enable accessibility service\n'
                      '2. Disable battery optimization\n'
                      '3. Enable autostart (Xiaomi)\n'
                      '4. Close the app\n'
                      '5. Make a call - it will record!\n\n'
                      '⚡ BATTERY OPTIMIZATION MUST BE DISABLED\n\n'
                      'Without this, Android will kill the recorder when you close the app.\n\n'
                      '📞 The app works in background like Cube ACR!',
                    ),
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('OK'),
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // Status Card
          Card(
            margin: const EdgeInsets.all(16),
            color: _isAccessibilityEnabled 
                ? Colors.green[50] 
                : Colors.red[50],
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(
                    _isAccessibilityEnabled
                        ? Icons.check_circle
                        : Icons.error,
                    color: _isAccessibilityEnabled
                        ? Colors.green
                        : Colors.red,
                    size: 32,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _isAccessibilityEnabled
                              ? '✅ Recording Active'
                              : '❌ Service Disabled',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          _isAccessibilityEnabled
                              ? 'Works even when app is closed!'
                              : 'Complete setup to start recording',
                          style: TextStyle(
                            fontSize: 14,
                            color: Colors.grey[700],
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (!_isAccessibilityEnabled)
                    ElevatedButton(
                      onPressed: _openAccessibilitySettings,
                      child: const Text('Setup'),
                    ),
                ],
              ),
            ),
          ),

          // Setup Warning (if not complete)
          if (!_isAccessibilityEnabled)
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.orange[50],
                border: Border.all(color: Colors.orange),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.warning, color: Colors.orange),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Complete setup to record calls in background!',
                      style: TextStyle(
                        color: Colors.orange[900],
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ),

          // Recordings Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Text(
                  '📁 Recordings (${_recordings.length})',
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
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
                        Icon(
                          Icons.folder_open,
                          size: 64,
                          color: Colors.grey[400],
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'No recordings yet',
                          style: TextStyle(
                            fontSize: 18,
                            color: Colors.grey[600],
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          _isAccessibilityEnabled
                              ? 'Make a call to test recording'
                              : 'Complete setup first',
                          style: TextStyle(
                            fontSize: 14,
                            color: Colors.grey[500],
                          ),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    itemCount: _recordings.length,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemBuilder: (context, index) {
                      final recording = _recordings[index];
                      final path = recording['path'] as String;
                      final name = recording['name'] as String;
                      final modified = recording['modified'] as DateTime;
                      final size = recording['size'] as int;

                      return Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: Colors.blue[100],
                            child: const Icon(
                              Icons.phone_in_talk,
                              color: Colors.blue,
                            ),
                          ),
                          title: Text(
                            name,
                            style: const TextStyle(fontSize: 13),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            '${_formatDateTime(modified)} • ${_formatFileSize(size)}',
                            style: const TextStyle(fontSize: 12),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.play_arrow),
                                onPressed: () {
                                  Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (context) =>
                                          AudioPlayerScreen(
                                        filePath: path,
                                        fileName: name,
                                      ),
                                    ),
                                  );
                                },
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete,
                                    color: Colors.red),
                                onPressed: () async {
                                  final confirm = await showDialog<bool>(
                                    context: context,
                                    builder: (context) => AlertDialog(
                                      title: const Text('Delete?'),
                                      content: const Text(
                                          'Delete this recording?'),
                                      actions: [
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, false),
                                          child: const Text('Cancel'),
                                        ),
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, true),
                                          child: const Text(
                                            'Delete',
                                            style: TextStyle(
                                                color: Colors.red),
                                          ),
                                        ),
                                      ],
                                    ),
                                  );

                                  if (confirm == true) {
                                    await File(path).delete();
                                    await _loadRecordings();
                                    if (mounted) {
                                      ScaffoldMessenger.of(context)
                                          .showSnackBar(
                                        const SnackBar(
                                            content: Text('Deleted')),
                                      );
                                    }
                                  }
                                },
                              ),
                            ],
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
}
