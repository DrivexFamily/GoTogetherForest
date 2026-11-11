"""Copy existing read-only anchor measurements into portable QA documents.

No pixel editing, new anatomical inference, image loading, or Git operations.
Usage: python core/ui/motion-docs/update-anchor-qa.py --manifest <review-json>
Use --finalize-dad only after root replaces/rechecks dad and updates that manifest.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path


def portable_path(value):
    if not value:
        return None
    value = str(value).replace('\\', '/')
    for marker in ('design/illustrations/', 'core/ui/src/main/res/'):
        position = value.find(marker)
        if position >= 0:
            return value[position:]
    return value if not Path(value).is_absolute() else None


def build(manifest_path, destination, finalize_dad):
    raw_bytes = manifest_path.read_bytes()
    data = json.loads(raw_bytes)
    assert len(data['items']) == data['count'] == 13
    output = []
    for item in data['items']:
        raw_measurements = item.get('foot_or_base_readonly_measurements', [])
        measurements = {record['frame_index']: record for record in raw_measurements}
        cells = {record['index']: record for record in item.get('cells', [])}
        active_count = item['active_frames']
        pending = item['id'].startswith('DAD-') and not finalize_dad
        frames = []
        for index in range(active_count):
            measurement = copy.deepcopy(measurements.get(index))
            cell = copy.deepcopy(cells.get(index))
            frames.append({
                'frame_index': index,
                'measurement_status': 'copied_existing_approximate_measurement' if measurement else 'not_measured',
                'original_measurement': measurement,
                'original_cell_alpha_measurement': cell,
                'exact_anatomical_foot_or_root_xy': None,
                'exact_anatomical_xy_status': 'not_measured; do not substitute desired_anchor x or ROI center',
                'independently_measured_shadow_bbox_xyxy': None,
                'independently_measured_shadow_status': 'not_recorded; complete alpha bbox may include shadow/wing/soil',
                'interpolation_or_remeasurement_performed': False,
            })
        output.append({
            'id': item['id'], 'resource_name': item['resource_name'],
            'source_png_path': portable_path(item.get('source_path')),
            'source_png_filename': Path(item['source_path']).name if item.get('source_path') else None,
            'source_png_path_status': 'repository_relative' if portable_path(item.get('source_path')) else 'repository_destination_not_yet_recorded',
            'source_png_sha256': item.get('source_sha256'),
            'native_png_path': portable_path(item.get('native_original_path')),
            'native_png_filename': Path(item['native_original_path']).name if item.get('native_original_path') else None,
            'native_png_sha256': item.get('native_original_sha256'),
            'app_webp_path': portable_path(item.get('app_resource_path')),
            'app_webp_sha256_in_source_manifest': item.get('app_resource_sha256'),
            'source_size': item.get('actual_size'), 'grid': item.get('grid'),
            'cell_size': item.get('cell_size'), 'active_frame_count': active_count,
            'measurement_coordinate_space': 'delivery PNG cell-local pixels; copied from source manifest; not remasked on WebP RGB',
            'root_final_refresh_required': pending,
            'status': 'dad_replacement_pending_remeasure_and_refresh' if pending else 'read_only_measurement_snapshot',
            'frames': frames,
            'all_source_cells_including_unused': copy.deepcopy(item.get('cells', [])),
            'native_cell_measurements_for_comparison': copy.deepcopy(item.get('native_cells', [])),
            'additional_frame_realignment_performed': False,
            'body_height_note': item.get('body_height_note'),
        })
    payload = {
        'schema_version': 1, 'request': 'P1-anchor-QA',
        'source_manifest_artifact': 'team/icon_sticker_artist/P1-assets-manifest.json',
        'source_manifest_sha256': hashlib.sha256(raw_bytes).hexdigest(),
        'source_manifest_finalized_at': data.get('finalized_at'),
        'source_measurement_owner': 'icon_sticker_artist',
        'count': len(output),
        'active_frames': sum(x['active_frame_count'] for x in output),
        'anatomical_exact_points_measured_by_this_export': 0,
        'independent_shadow_boxes_measured_by_this_export': 0,
        'no_new_measurements_or_pixel_changes': True,
        'coordinate_conventions': {
            'frame_indices': 'zero based; row major',
            'source_rect_xyxy': 'existing alpha bbox/ROI coordinates retained; bbox exclusive-bottom differs from recorded inclusive bottom',
            'y_direction': 'downward',
            'animal_family_virtual_ground_xy': [256, 460],
            'oak_root_soil_proxy_xy': [192, 964],
            'warning': 'virtual anchor x is not measured anatomical foot x; root/soil proxy is not isolated root anatomy',
        },
        'limitations': [
            'Actual visible foot/shoe/root lower-y entries are color-mask proxies with original ROI/method/uncertainty copied unchanged.',
            'Shadow-only coordinates and exact anatomical (x,y) are not recorded in the source; null means not measured.',
            'For airborne frames, foot y below 460 means rising above the virtual ground; do not move every foot back to y460.',
            'OAK-GROW uses y964 in a 384x1024 cell, not y460.',
            'WebP alpha roundtrip equality is recorded by asset QA; original color-mask foot coordinates were not remeasured on lossy RGB.',
            'When replacing dad artwork, old measurements cannot be inherited as measurements of the new source; update source manifest measurements first.',
        ],
        'pending_root_final_refresh_ids': [x['id'] for x in output if x['root_final_refresh_required']],
        'items': output,
    }
    encoded = json.dumps(payload, ensure_ascii=False, indent=2) + '\n'
    assert '/workspace/' not in encoded and '/tmp/' not in encoded
    destination.mkdir(parents=True, exist_ok=True)
    temp = destination / 'ANCHOR-QA.json.tmp'
    temp.write_text(encoded)
    temp.replace(destination / 'ANCHOR-QA.json')
    lines = [
        '# P1 발·그림자·뿌리 앵커 QA', '',
        '기존 icon 담당 명세의 **13개 시트·43개 활성 프레임** 측정을 그대로 옮겼다. 이 문서 작성 중 새 좌표 측정이나 원본 수정은 하지 않았다. [ANCHOR-QA.json](ANCHOR-QA.json)에 원래 ROI·색 마스크 방식·근사 구간·불확실성·셀 bbox·원본 SHA를 보존했다.', '',
        '좌표는 납품 PNG의 **셀 내부 픽셀**, 프레임은 0부터다. 표의 발/뿌리 Y는 실제로 보이는 부위의 **색 마스크 하단 근사값**이며 해부학적 발바닥/뿌리 좌표가 확정됐다는 뜻이 아니다. 원래 측정은 WebP RGB에서 재수행하지 않았다. 표의 순서는 활성 프레임 순서다.', '',
        '| 시트 | 발·신발·뿌리 색 하단 Y(근사) | 전체 alpha128 하단 Y(inclusive) | 가상 지면/기준 Y | 원본 측정의 Y−기준 | 상태 |',
        '| --- | --- | --- | --- | --- | --- |',
    ]
    for item in output:
        def sequence(field):
            return ', '.join(str((f['original_measurement'] or {}).get(field, '미측정')) for f in item['frames'])
        targets = [(f['original_measurement'] or {}).get('desired_anchor_local') for f in item['frames']]
        target_values = sorted(set(t[1] for t in targets if t))
        target = ', '.join(map(str, target_values)) or '미측정'
        state = '**아빠 교체·재측정 대기**' if item['root_final_refresh_required'] else '기존 근사 측정'
        lines.append(f"| {item['id']} | {sequence('selected_visible_extremity_lower_y')} | {sequence('all_alpha128_bottom_inclusive')} | {target} | {sequence('selected_lower_y_minus_target')} | {state} |")
    dad_note = (
        '- 아빠 WAVE/JUMP는 교체 중이다. 현재 표는 검토 중 스냅샷이며 최종 아빠에 유효하다고 간주하면 안 된다. 교체 후 icon 담당 원 명세의 ROI·측정·SHA를 먼저 갱신하고 아래 스크립트로 재생성한다. 다른 11개 측정은 변경하지 않는다.'
        if payload['pending_root_final_refresh_ids'] else
        '- root의 최종 아빠 확인 옵션으로 갱신했다. 아빠의 현재 출처 SHA와 측정은 갱신된 원 명세를 따른다. 이 플래그는 해부학적 발/그림자를 새로 측정하거나 확정하는 기능이 아니며, 원 기록의 근사치·미측정 한계는 그대로다.'
    )
    lines += [
        '', '## 해석과 미측정 항목', '',
        '- 동물·가족의 **[256,460]은 가상 지면 기준**이며 실제 발의 x좌표가 256으로 측정된 것이 아니다. 별도의 해부학적 발/뿌리 (x,y), 좌우 발 각각의 좌표는 **미측정(null)**이다. ROI 중심을 발 좌표로 만들지 않았다.',
        '- **그림자만 따로 분리한 bbox/좌표도 미측정(null)**이다. 전체 alpha bbox는 그림자·날개·흙을 포함할 수 있으므로 그림자 위치나 발 위치로 자동 해석하지 않는다. 각 셀의 원래 alpha bbox는 JSON에 별도로 보존했다.',
        '- 예: 다람쥐 HOP frame2의 발색 하단은 **384**, 전체 alpha 하단은 **452**다. 이전 시각 QA에서 아래의 분리 그림자를 구분했다. 파랑새 FLY frame2의 발색 하단은 **450**, 전체 하단 **460**은 날개 끝을 포함한다. 여기서 새로운 그림자 단독 좌표를 역산하지 않는다.',
        '- 점프/비행 중 발 하단이 460보다 작으면 위로 올라온 동작이다. 다람쥐 HOP frame2는 원래 측정의 기준 차가 **−76px**, 엄마 JUMP frame2는 **−63px**다. 모든 프레임의 발을 460으로 옮기는 보정은 하지 않는다. 근사 색 하단과 실제 접지 여부도 구분한다.',
        '- 참나무 4×1 셀은 **384×1024**, 기준 **[192,964]**다. 뿌리/흙 색 하단은 네 셀 모두 **964**로 기록됐지만 흙·작은 접촉 그림자와 해부학적 뿌리를 분리하지 못한 근사값이다. 동물의 y460 규칙을 적용하지 않는다.',
        '- 동물의 근사 구간은 원래 QA가 제시한 시각적 휴리스틱이며 통계적 신뢰구간이 아니다. 가족의 어두운 신발 밑창/그림자 경계는 불확실하여 원래 구간 null을 그대로 보존했다.',
        dad_note,
        '', '## 갱신 방법', '',
        '저장소 루트에서 실행한다. 첫 명령은 아빠를 계속 보류 상태로 표시한다. 최종 아빠 원본과 측정을 확인한 root만 두 번째 옵션을 사용한다. 스크립트는 JSON/Markdown만 쓰고 이미지·Kotlin·Git은 수정하지 않는다.', '',
        '```sh',
        'python core/ui/motion-docs/update-anchor-qa.py --manifest /path/to/P1-assets-manifest.json',
        'python core/ui/motion-docs/update-anchor-qa.py --manifest /path/to/final/P1-assets-manifest.json --finalize-dad',
        '```', '',
        '원본 명세에 새 측정이 없으면 새 항목도 미측정으로 남긴다. `--finalize-dad`는 측정을 새로 만드는 명령이 아니며 이전 아빠 좌표를 새 그림에 검증하는 기능도 아니다.',
    ]
    (destination / 'ANCHOR-QA.md').write_text('\n'.join(lines) + '\n')
    print(json.dumps({'count':len(output),'active_frames':payload['active_frames'],'pending':payload['pending_root_final_refresh_ids'],'sha256':hashlib.sha256(encoded.encode()).hexdigest()}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument('--finalize-dad', action='store_true')
    arguments = parser.parse_args()
    build(arguments.manifest, arguments.output_dir, arguments.finalize_dad)
