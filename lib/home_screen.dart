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
  List<FileSystemEntity> _recordings = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    await _requestPermissions();
    await _checkAccessibility();
    await _loadRecordings();
    if (mounted) {
      setState(() {
        _isLoading = false;
      });
    }
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
      // FIXED: Use correct path matching native code
      final directory = Directory('/data/data/com.example.recorder/files/CallRecordings');

      if (await directory.exists()) {
        final files = directory.listSync()
            .where((file) => file.path.endsWith('.m4a'))
            .toList();
        
        files.sort((a, b) => 
            b.statSync().modified.compareTo(a.statSync().modified));

        if (mounted) {
          setState(() {
            _recordings = files;
          });
        }
      }
    } catch (e) {
      debugPrint('Error loading recordings: $e');
      // Try alternative path
      try {
        final appDir = await getApplicationDocumentsDirectory();
        final altDirectory = Directory('${appDir.path}/CallRecordings');
        if (await altDirectory.exists()) {
          final files = altDirectory.listSync()
              .where((file) => file.path.endsWith('.m4a'))
              .toList();
          files.sort((a, b) => 
              b.statSync().modified.compareTo(a.statSync().modified));
          if (mounted) {
            setState(() {
              _recordings = files;
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
          title: const Text('✅ Enable Service'),
          content: const Text(
            'Steps:\n'
            '1. Find "Company Call Recorder"\n'
            '2. Turn ON the service\n'
            '3. Allow restricted settings if asked\n'
            '4. Return to app\n\n'
            '📱 Calls will record automatically!',
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
                              ? 'Calls will be recorded automatically'
                              : 'Enable service to start recording',
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
                      child: const Text('Enable'),
                    ),
                ],
              ),
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
                              : 'Enable service to start',
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
                      final file = File(_recordings[index].path);
                      final stat = file.statSync();
                      final fileName = file.path.split('/').last;

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
                            fileName,
                            style: const TextStyle(fontSize: 13),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            '${_formatDateTime(stat.modified)} • ${_formatFileSize(stat.size)}',
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
                                        filePath: file.path,
                                        fileName: fileName,
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
                                    await file.delete();
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
