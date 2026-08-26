import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LIST_SIZE = __ENV.SIZE || '10';
const HOT_POST_COUNT = Number(__ENV.HOT_POST_COUNT || 1);
const LIST_RATIO = Number(__ENV.LIST_RATIO || '0.7');
const RATE = Number(__ENV.RATE || 50);
const DURATION = __ENV.DURATION || '30s';
const PREALLOCATED_VUS = Number(__ENV.PREALLOCATED_VUS || 20);
const MAX_VUS = Number(__ENV.MAX_VUS || 50);

const non200Responses = new Counter('mixed_non_200');

export const options = {
  scenarios: {
    mixed: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PREALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    mixed_non_200: ['count==0'],
  },
};

function recordResponse(response, endpoint) {
  if (response.status !== 200) {
    non200Responses.add(1, {
      endpoint,
      status: String(response.status),
    });
  }
}

export function setup() {
  const listUrl = `${BASE_URL}/api/posts?page=1&size=${LIST_SIZE}&keyword=&sort=ID_DESC`;
  const listResponse = http.get(listUrl);
  recordResponse(listResponse, 'setup-list');

  check(listResponse, {
    'mixed setup list status is 200': (r) => r.status === 200,
  });

  const body = JSON.parse(listResponse.body);
  const ids = body.data.items.slice(0, HOT_POST_COUNT).map((item) => item.id);

  ids.forEach((id) => {
    const warmup = http.get(`${BASE_URL}/api/posts/${id}`);
    recordResponse(warmup, 'warmup-detail');
    check(warmup, {
      'mixed warmup detail status is 200': (r) => r.status === 200,
    });
  });

  return { listUrl, ids };
}

export default function (data) {
  if (Math.random() < LIST_RATIO) {
    const response = http.get(data.listUrl);
    recordResponse(response, 'list');
    check(response, {
      'mixed list status is 200': (r) => r.status === 200,
    });
    return;
  }

  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const response = http.get(`${BASE_URL}/api/posts/${id}`);
  recordResponse(response, 'detail');
  check(response, {
    'mixed detail status is 200': (r) => r.status === 200,
  });
}
