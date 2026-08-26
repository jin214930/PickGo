import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HOT_POST_COUNT = Number(__ENV.HOT_POST_COUNT || 5);
const SLEEP_SEC = Number(__ENV.SLEEP_SEC || '1');

export const options = {
  vus: Number(__ENV.VUS || 30),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const listResponse = http.get(`${BASE_URL}/api/posts?page=1&size=10&sort=ID_DESC`);

  check(listResponse, {
    'detail setup list status is 200': (r) => r.status === 200,
  });

  const body = JSON.parse(listResponse.body);
  const ids = body.data.items.slice(0, HOT_POST_COUNT).map((item) => item.id);

  ids.forEach((id) => {
    const warmup = http.get(`${BASE_URL}/api/posts/${id}`);
    check(warmup, {
      'warmup detail status is 200': (r) => r.status === 200,
    });
  });

  return { ids };
}

export default function (data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const response = http.get(`${BASE_URL}/api/posts/${id}`);

  check(response, {
    'detail status is 200': (r) => r.status === 200,
    'detail has performance': (r) => {
      const body = JSON.parse(r.body);
      return body.data.performance && body.data.performance.id;
    },
  });

  if (SLEEP_SEC > 0) {
    sleep(SLEEP_SEC);
  }
}
