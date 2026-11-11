"""기존 U03 원본을 읽어 앱용 축소 WebP만 만든다. 원본은 수정하지 않는다."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, __version__ as pillow_version, features

SELECTED = {
    'ATLAS-PAGE-DAY', 'ATLAS-PAGE-EVENING', 'ATLAS-REGION-COMPLETE',
    'ATLAS-EMPTY', 'ATLAS-MYSTERY', 'ATLAS-BOOK-OPEN', 'ACQUIRE-FLAG',
    'ACQUIRE-RIBBON', 'ACQUIRE-RAYS', 'ACQUIRE-SPARKLES',
    'ACQUIRE-STAGE-DAY', 'ACQUIRE-STAGE-EVENING',
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-repo', type=Path, required=True)
    parser.add_argument('--app-repo', type=Path, default=Path(__file__).resolve().parents[4])
    args = parser.parse_args()
    source_root, app_root = args.source_repo.resolve(), args.app_repo.resolve()
    source_dir = source_root / 'design/illustrations/exports/2026-11-06'
    source_manifest = source_dir / 'U03-delivery-manifest.json'
    selected = [x for x in json.loads(source_manifest.read_text())['items']
                if x['id'].startswith('REGION-') or x['id'] in SELECTED]
    assert len(selected) == 29
    output = app_root / 'feature/atlas/src/main/res/drawable-nodpi'
    output.mkdir(parents=True, exist_ok=True)
    records = []
    for entry in selected:
        source = source_dir / entry['file']
        source_hash = digest(source)
        assert source_hash == entry['sha256'], entry['id']
        key = entry['id']
        name = ('ggf_atlas_region_' + key.removeprefix('REGION-').lower()
                if key.startswith('REGION-') else 'ggf_' + key.lower().replace('-', '_'))
        is_background = key in {'ATLAS-PAGE-DAY', 'ATLAS-PAGE-EVENING',
                                'ACQUIRE-STAGE-DAY', 'ACQUIRE-STAGE-EVENING'}
        max_edge = 1280 if is_background else 512
        with Image.open(source) as original:
            assert original.mode in {'RGB', 'RGBA'}
            original_size, original_mode = list(original.size), original.mode
            image = original.copy()
        image.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)
        assert image.width <= original_size[0] and image.height <= original_size[1]
        target = output / (name + '.webp')
        image.save(target, 'WEBP', quality=92, alpha_quality=100, method=6, exact=True)
        expected = np.asarray(image)
        with Image.open(target) as decoded:
            assert decoded.size == image.size
            actual = np.asarray(decoded.convert(image.mode))
            decoded_mode = decoded.mode
        if image.mode == 'RGBA':
            alpha_equal = bool(np.array_equal(expected[:, :, 3], actual[:, :, 3]))
            assert alpha_equal
            visible = expected[:, :, 3] > 15
            alpha = expected[:, :, 3]
            alpha_metadata = {
                'output_alpha_exact_in_WebP_roundtrip': alpha_equal,
                'resized_alpha_nonzero_pixels': int((alpha > 0).sum()),
                'resized_alpha_1_to_15_pixels': int(((alpha > 0) & (alpha <= 15)).sum()),
                'extra_alpha_threshold_or_cleanup': False,
            }
        else:
            visible = np.ones(expected.shape[:2], dtype=bool)
            alpha_metadata = {'output_alpha_exact_in_WebP_roundtrip': None,
                              'extra_alpha_threshold_or_cleanup': False}
        rgb_difference = np.abs(expected[:, :, :3].astype(np.int16) -
                                actual[:, :, :3].astype(np.int16))
        rgb_mae = float(rgb_difference[visible].mean()) if visible.any() else 0.0
        assert digest(source) == source_hash
        records.append({
            'id': key, 'resource_name': name,
            'source_png': source.relative_to(source_root).as_posix(),
            'source_sha256': source_hash, 'source_size': original_size,
            'source_mode': original_mode, 'source_bytes': source.stat().st_size,
            'app_webp': target.relative_to(app_root).as_posix(),
            'app_sha256': digest(target), 'app_size': list(image.size),
            'app_mode': decoded_mode, 'app_bytes': target.stat().st_size,
            'downsampled': list(image.size) != original_size,
            'resize_filter': 'LANCZOS, aspect-ratio preserved within integer rounding',
            'codec': 'WebP RGB quality92; lossless alpha quality100',
            'alpha': alpha_metadata,
            'rgb_codec_MAE_over_visible_pixels': rgb_mae,
            'upscale': False, 'color_filter': False, 'crop': False,
            'native_original_changed': False,
        })
    result = {
        'request': 'U03-Atlas-Android', 'count': len(records),
        'region_covers': sum(x['id'].startswith('REGION-') for x in records),
        'total_app_webp_bytes': sum(x['app_bytes'] for x in records),
        'source_manifest': source_manifest.relative_to(source_root).as_posix(),
        'source_manifest_sha256': digest(source_manifest),
        'encoder': {'Pillow': pillow_version, 'libwebp': features.version('webp')},
        'native_originals_preserved': True,
        'source_review': 'Read-only browser contact sheet: 29 sources directly viewed.',
        'Android_UI_runtime_test': False,
        'notes': [
            'Region covers are imaginative montages, not maps or accurate landmark adjacency.',
            'Atlas native PNG originals remain on main; only app derivatives belong on the code branch.',
            'Shrinking resamples edge alpha; WebP alpha is identical to that resized intermediate.',
            'Normal lossy RGB codec differences are recorded; no color grading/filter was applied.',
        ],
        'items': records,
    }
    manifest = app_root / 'feature/atlas/docs/atlas-assets/manifest.json'
    manifest.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'assets': len(records), 'bytes': result['total_app_webp_bytes']}))


if __name__ == '__main__':
    main()
