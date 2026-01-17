// File: lib/screens/home_screen.dart
import 'package:flutter/material.dart';
import 'dart:io';
import 'package:path_provider/path_provider.dart';

import 'audioplayerscreen.dart';
import 'call_recorder_service.dart';
import 'native_recorder_bridge.dart';


class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final CallRecorderService _recorderService = CallRecorderService();
  bool _isInitialized = false;
  bool _isAccessibilityEnabled = false;
  bool _isRecording = false;
  List<FileSystemEntity> _recordings = [];

  @override
  void initState() {
    super.initState();
    _initializeApp();
  }

  Future<void> _initializeApp() async {
    await _checkAccessibilityStatus();
    await _loadRecordings();
  }

  Future<void> _checkAccessibilityStatus() async {
    final enabled = await NativeRecorderBridge.isAccessibilityServiceEnabled();
    setState(() {
      _isAccessibilityEnabled = enabled;
    });

    if (enabled) {
      await _initializeRecorderService();
    }
  }

  Future<void> _initializeRecorderService() async {
    final success = await _recorderService.initialize();
    setState(() {
      _isInitialized = success;
    });
  }

  Future<void> _loadRecordings() async {
    try {
      final directory = await getApplicationDocumentsDirectory();
      final recordingsDir = Directory('${directory.path}/CallRecordings');

      if (await recordingsDir.exists()) {
        final files = recordingsDir.listSync()
            .where((file) => file.path.endsWith('.m4a'))
            .toList();
        files.sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));

        setState(() {
          _recordings = files;
        });
      }
    } catch (e) {
      debugPrint('Error loading recordings: $e');
    }
  }

  Future<void> _openAccessibilitySettings() async {
    await NativeRecorderBridge.openAccessibilitySettings();

    // Show dialog with instructions
    if (mounted) {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Enable Accessibility Service'),
          content: const Text(
            'Follow these steps:\n\n'
                '1. Find "Company Call Recorder" in the list\n'
                '2. Turn ON the service\n'
                '3. If you see "Restricted", tap the 3-dot menu (⋮) and select "Allow restricted settings"\n'
                '4. Come back to the app\n\n'
                'This is required for call recording to work.',
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

  Future<void> _testRecording() async {
    if (_isRecording) {
      // Stop recording
      final savedPath = await _recorderService.stopManualRecording();
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
      // Start recording
      final success = await _recorderService.startManualRecording();
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
        title: const Text('Company Call Recorder'),
        actions: [
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
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        _isAccessibilityEnabled
                            ? Icons.check_circle
                            : Icons.error,
                        color: _isAccessibilityEnabled
                            ? Colors.green
                            : Colors.red,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Accessibility Service',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const Spacer(),
                      Text(
                        _isAccessibilityEnabled ? 'Enabled' : 'Disabled',
                        style: TextStyle(
                          color: _isAccessibilityEnabled
                              ? Colors.green
                              : Colors.red,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                  if (!_isAccessibilityEnabled) ...[
                    const SizedBox(height: 12),
                    ElevatedButton.icon(
                      onPressed: _openAccessibilitySettings,
                      icon: const Icon(Icons.settings),
                      label: const Text('Enable Service'),
                    ),
                  ],
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Icon(
                        _isInitialized
                            ? Icons.check_circle
                            : Icons.warning,
                        color: _isInitialized
                            ? Colors.green
                            : Colors.orange,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Recording Service',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const Spacer(),
                      Text(
                        _isInitialized ? 'Active' : 'Inactive',
                        style: TextStyle(
                          color: _isInitialized
                              ? Colors.green
                              : Colors.orange,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Test Recording Button
          if (_isAccessibilityEnabled)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: _testRecording,
                  icon: Icon(_isRecording ? Icons.stop : Icons.mic),
                  label: Text(_isRecording ? 'Stop Test Recording' : 'Start Test Recording'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _isRecording ? Colors.red : Colors.blue,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.all(16),
                  ),
                ),
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
                    style: TextStyle(color: Colors.grey[600]),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Recordings will appear here automatically',
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
                  child: ListTile(
                    leading: const CircleAvatar(
                      child: Icon(Icons.phone_in_talk),
                    ),
                    title: Text(
                      fileName,
                      style: const TextStyle(fontSize: 14),
                    ),
                    subtitle: Text(
                      '${_formatDateTime(stat.modified)} • ${_formatFileSize(stat.size)}',
                      style: const TextStyle(fontSize: 12),
                    ),
                    // Add this onTap handler
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
                            content: const Text('Are you sure you want to delete this recording?'),
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

  @override
  void dispose() {
    _recorderService.dispose();
    super.dispose();
  }
}