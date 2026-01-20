import 'package:flutter/material.dart';
import 'package:audioplayers/audioplayers.dart';
import 'dart:io';

class AudioPlayerScreen extends StatefulWidget {
  final String filePath;
  final String fileName;

  const AudioPlayerScreen({
    super.key,
    required this.filePath,
    required this.fileName,
  });

  @override
  State<AudioPlayerScreen> createState() => _AudioPlayerScreenState();
}

class _AudioPlayerScreenState extends State<AudioPlayerScreen> {
  AudioPlayer? _audioPlayer;
  bool _isPlaying = false;

  @override
  void initState() {
    super.initState();
    _audioPlayer = AudioPlayer();
  }

  Future<void> _play() async {
    try {
      await _audioPlayer?.play(DeviceFileSource(widget.filePath));
      if (mounted) {
        setState(() => _isPlaying = true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    }
  }

  Future<void> _pause() async {
    await _audioPlayer?.pause();
    if (mounted) {
      setState(() => _isPlaying = false);
    }
  }

  Future<void> _stop() async {
    await _audioPlayer?.stop();
    if (mounted) {
      setState(() => _isPlaying = false);
    }
  }

  @override
  void dispose() {
    _audioPlayer?.stop();
    _audioPlayer?.dispose();
    _audioPlayer = null;
    super.dispose();
  }

  String _getFileSize() {
    try {
      final file = File(widget.filePath);
      final bytes = file.lengthSync();
      final mb = (bytes / (1024 * 1024)).toStringAsFixed(2);
      return '$mb MB';
    } catch (e) {
      return 'Unknown';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('🎵 Play Recording'),
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Icon
              Container(
                width: 150,
                height: 150,
                decoration: BoxDecoration(
                  color: Colors.blue[100],
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.phone_in_talk,
                  size: 80,
                  color: Colors.blue[700],
                ),
              ),

              const SizedBox(height: 40),

              // File name
              Text(
                widget.fileName,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),

              const SizedBox(height: 8),

              // File size
              Text(
                _getFileSize(),
                style: TextStyle(
                  fontSize: 14,
                  color: Colors.grey[600],
                ),
              ),

              const SizedBox(height: 60),

              // Controls
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Play button
                  ElevatedButton.icon(
                    onPressed: _isPlaying ? null : _play,
                    icon: const Icon(Icons.play_arrow, size: 32),
                    label: const Text('Play'),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 24,
                        vertical: 16,
                      ),
                      backgroundColor: Colors.green,
                      foregroundColor: Colors.white,
                    ),
                  ),

                  const SizedBox(width: 16),

                  // Pause button
                  ElevatedButton.icon(
                    onPressed: _isPlaying ? _pause : null,
                    icon: const Icon(Icons.pause, size: 32),
                    label: const Text('Pause'),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 24,
                        vertical: 16,
                      ),
                      backgroundColor: Colors.orange,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 16),

              // Stop button
              ElevatedButton.icon(
                onPressed: _isPlaying ? _stop : null,
                icon: const Icon(Icons.stop, size: 32),
                label: const Text('Stop'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 16,
                  ),
                  backgroundColor: Colors.red,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
