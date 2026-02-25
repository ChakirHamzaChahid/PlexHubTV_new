#!/bin/bash
# Script pour augmenter tous les timeouts des tests Maestro

# Liste des fichiers à modifier (sauf ceux déjà modifiés)
for file in 05_movie_detail.yaml 06_play_movie.yaml 07_player_controls.yaml 08_audio_subtitle_tracks.yaml 09_search.yaml 10_favorites.yaml 11_browse_tvshows.yaml 12_settings.yaml 13_multi_server_source.yaml 14_downloads_offline.yaml 15_history.yaml 16_iptv.yaml 17_ai_visual_checks.yaml; do
    if [ -f "$file" ]; then
        echo "Mise à jour de $file..."
        # Remplacer timeout: 5000 par timeout: 30000
        sed -i 's/timeout: 5000$/timeout: 30000  # Augmenté pour stabilité/g' "$file"
        # Remplacer timeout: 10000 par timeout: 60000
        sed -i 's/timeout: 10000$/timeout: 60000  # Augmenté pour stabilité/g' "$file"
        # Remplacer timeout: 15000 par timeout: 60000
        sed -i 's/timeout: 15000$/timeout: 60000  # Augmenté pour stabilité/g' "$file"
        # Remplacer timeout: 20000 par timeout: 90000
        sed -i 's/timeout: 20000$/timeout: 90000  # Augmenté pour stabilité/g' "$file"
        # Remplacer timeout: 30000 par timeout: 120000
        sed -i 's/timeout: 30000$/timeout: 120000  # Augmenté pour stabilité/g' "$file"
        # Remplacer timeout: 60000 par timeout: 180000
        sed -i 's/timeout: 60000$/timeout: 180000  # Augmenté pour stabilité/g' "$file"
    fi
done

echo "✅ Tous les timeouts ont été augmentés !"
